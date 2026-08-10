package ru.itmo.nemat.weezzy.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlock;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockRepository;
import ru.itmo.nemat.weezzy.connection.event.ProfileInteractionEvent;
import ru.itmo.nemat.weezzy.connection.event.ProfileInteractionEventRepository;
import ru.itmo.nemat.weezzy.connection.event.ProfileInteractionEventType;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchRepository;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVoteRepository;
import ru.itmo.nemat.weezzy.goal.Goal;
import ru.itmo.nemat.weezzy.goal.GoalRepository;
import ru.itmo.nemat.weezzy.interest.Interest;
import ru.itmo.nemat.weezzy.interest.InterestRepository;
import ru.itmo.nemat.weezzy.interest.suggestion.InterestSuggestion;
import ru.itmo.nemat.weezzy.interest.suggestion.InterestSuggestionRepository;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoal;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoalRepository;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterest;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterestRepository;
import ru.itmo.nemat.weezzy.profile.photo.ProfilePhoto;
import ru.itmo.nemat.weezzy.profile.photo.ProfilePhotoRepository;
import ru.itmo.nemat.weezzy.profile.photo.ProfilePhotoStatus;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkill;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillRepository;
import ru.itmo.nemat.weezzy.recommendation.impression.ProfileRecommendationImpression;
import ru.itmo.nemat.weezzy.recommendation.impression.ProfileRecommendationImpressionRepository;
import ru.itmo.nemat.weezzy.security.session.AuthSessionRepository;
import ru.itmo.nemat.weezzy.skill.Skill;
import ru.itmo.nemat.weezzy.skill.SkillRepository;
import ru.itmo.nemat.weezzy.skill.suggestion.SkillSuggestion;
import ru.itmo.nemat.weezzy.skill.suggestion.SkillSuggestionRepository;
import ru.itmo.nemat.weezzy.storage.ObjectStorageService;
import ru.itmo.nemat.weezzy.user.emailverification.EmailVerificationTokenRepository;
import ru.itmo.nemat.weezzy.user.emailverification.LocalEmailVerificationSender;
import ru.itmo.nemat.weezzy.user.passwordreset.LocalPasswordResetSender;
import ru.itmo.nemat.weezzy.user.passwordreset.PasswordResetTokenRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AccountDeletionControllerTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");
	private static final String PASSWORD = "password123";

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private ProfileSkillRepository profileSkillRepository;
	@Autowired
	private ProfileInterestRepository profileInterestRepository;
	@Autowired
	private ProfileGoalRepository profileGoalRepository;
	@Autowired
	private ProfilePhotoRepository profilePhotoRepository;
	@Autowired
	private SkillRepository skillRepository;
	@Autowired
	private InterestRepository interestRepository;
	@Autowired
	private GoalRepository goalRepository;
	@Autowired
	private ProfileVoteRepository voteRepository;
	@Autowired
	private ProfileMatchRepository matchRepository;
	@Autowired
	private ProfileBlockRepository blockRepository;
	@Autowired
	private ProfileInteractionEventRepository interactionEventRepository;
	@Autowired
	private ProfileRecommendationImpressionRepository impressionRepository;
	@Autowired
	private SkillSuggestionRepository skillSuggestionRepository;
	@Autowired
	private InterestSuggestionRepository interestSuggestionRepository;
	@Autowired
	private AuthSessionRepository authSessionRepository;
	@Autowired
	private EmailVerificationTokenRepository emailVerificationTokenRepository;
	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;
	@MockitoBean
	private ObjectStorageService objectStorageService;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void deleteAccountRequiresAuthentication() throws Exception {
		mockMvc.perform(delete("/api/users/me")
					.contentType(MediaType.APPLICATION_JSON)
					.content(deleteAccountBody(PASSWORD)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void deleteAccountRejectsWrongCurrentPassword() throws Exception {
		TestAccount account = registerAccount("delete-wrong-password@itmo.ru");

		deleteAccount(account, "wrong-password")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Current password is invalid"));

		assertThat(userRepository.existsById(account.userId())).isTrue();
		mockMvc.perform(get("/api/auth/me")
					.header(HttpHeaders.AUTHORIZATION, bearer(account.accessToken())))
				.andExpect(status().isOk());
	}

	@Test
	void deleteAccountAnonymizesProfileAndPreservesSharedHistory() throws Exception {
		TestAccount deletedAccount = registerAccount("delete-account@itmo.ru");
		TestAccount survivor = registerAccount("delete-survivor@itmo.ru");
		createMutualMatch(deletedAccount, survivor);
		createPrivateData(deletedAccount, survivor);
		requestPasswordReset(deletedAccount.email());
		assertPrivateAuthDataExists(deletedAccount.userId());

		deleteAccount(deletedAccount, PASSWORD)
				.andExpect(status().isNoContent());

		assertAccountAndPrivateDataDeleted(deletedAccount, survivor);
		assertSharedHistoryPreserved(deletedAccount, survivor);
		assertDeletedProfileVisibleAsTombstone(deletedAccount, survivor);
		assertInteractionsWithDeletedProfileAreRejected(deletedAccount, survivor);
		assertOldTokensAreRejected(deletedAccount);

		registerOnly(deletedAccount.email())
				.andExpect(status().isCreated());
	}

	private void createMutualMatch(TestAccount first, TestAccount second) throws Exception {
		vote(first, second.profileId()).andExpect(status().isOk());
		vote(second, first.profileId()).andExpect(status().isOk());
		assertThat(matchRepository.findAll()).hasSize(1);
	}

	private void createPrivateData(TestAccount account, TestAccount other) {
		Profile profile = profileRepository.findById(account.profileId()).orElseThrow();
		ProfilePhoto photo = new ProfilePhoto();
		photo.setProfile(profile);
		photo.setObjectKey("profiles/" + account.profileId() + "/account-delete-photo");
		photo.setContentType("image/jpeg");
		photo.setSizeBytes(100L);
		photo.setPosition(0);
		photo.setIsAvatar(true);
		photo.setStatus(ProfilePhotoStatus.READY);
		photo.setUploadedAt(LocalDateTime.now());
		profilePhotoRepository.save(photo);

		Skill skill = new Skill();
		skill.setName("Delete skill " + UUID.randomUUID());
		skill = skillRepository.save(skill);
		ProfileSkill profileSkill = new ProfileSkill();
		profileSkill.setProfileId(account.profileId());
		profileSkill.setSkillId(skill.getId());
		profileSkillRepository.save(profileSkill);

		Interest interest = new Interest();
		interest.setName("Delete interest " + UUID.randomUUID());
		interest = interestRepository.save(interest);
		ProfileInterest profileInterest = new ProfileInterest();
		profileInterest.setProfileId(account.profileId());
		profileInterest.setInterestId(interest.getId());
		profileInterestRepository.save(profileInterest);

		Goal goal = new Goal();
		goal.setCode(
				"DELETE_" + UUID.randomUUID().toString().replace("-", "").toUpperCase()
		);
		goal.setName("Delete goal " + UUID.randomUUID());
		goal = goalRepository.save(goal);
		ProfileGoal profileGoal = new ProfileGoal();
		profileGoal.setProfileId(account.profileId());
		profileGoal.setGoalId(goal.getId());
		profileGoalRepository.save(profileGoal);

		User user = userRepository.findById(account.userId()).orElseThrow();
		SkillSuggestion skillSuggestion = new SkillSuggestion();
		skillSuggestion.setSuggestedBy(user);
		skillSuggestion.setName("Delete suggestion " + UUID.randomUUID());
		skillSuggestionRepository.save(skillSuggestion);
		InterestSuggestion interestSuggestion = new InterestSuggestion();
		interestSuggestion.setSuggestedBy(user);
		interestSuggestion.setName("Delete suggestion " + UUID.randomUUID());
		interestSuggestionRepository.save(interestSuggestion);

		User otherUser = userRepository.findById(other.userId()).orElseThrow();
		SkillSuggestion reviewedSkillSuggestion = new SkillSuggestion();
		reviewedSkillSuggestion.setSuggestedBy(otherUser);
		reviewedSkillSuggestion.setReviewedBy(user);
		reviewedSkillSuggestion.setName("Reviewed suggestion " + UUID.randomUUID());
		skillSuggestionRepository.save(reviewedSkillSuggestion);
		InterestSuggestion reviewedInterestSuggestion = new InterestSuggestion();
		reviewedInterestSuggestion.setSuggestedBy(otherUser);
		reviewedInterestSuggestion.setReviewedBy(user);
		reviewedInterestSuggestion.setName("Reviewed suggestion " + UUID.randomUUID());
		interestSuggestionRepository.save(reviewedInterestSuggestion);

		ProfileRecommendationImpression impression =
				new ProfileRecommendationImpression();
		impression.setSourceProfileId(account.profileId());
		impression.setTargetProfileId(other.profileId());
		impression.setShownAt(LocalDateTime.now());
		impressionRepository.save(impression);

		ProfileInteractionEvent event = new ProfileInteractionEvent();
		event.setSourceProfileId(account.profileId());
		event.setTargetProfileId(other.profileId());
		event.setEventType(ProfileInteractionEventType.LIKE);
		interactionEventRepository.save(event);

		ProfileBlock block = new ProfileBlock();
		block.setBlockerProfileId(account.profileId());
		block.setBlockedProfileId(other.profileId());
		blockRepository.save(block);
	}

	private void assertPrivateAuthDataExists(UUID userId) {
		assertThat(authSessionRepository.existsByUserId(userId)).isTrue();
		assertThat(emailVerificationTokenRepository.existsByUserId(userId)).isTrue();
		assertThat(passwordResetTokenRepository.existsByUserId(userId)).isTrue();
	}

	private void assertAccountAndPrivateDataDeleted(
			TestAccount account,
			TestAccount survivor
	) {
		assertThat(userRepository.existsById(account.userId())).isFalse();
		Profile tombstone = profileRepository.findById(account.profileId()).orElseThrow();
		assertThat(tombstone.getDisplayName()).isEqualTo("Deleted account");
		assertThat(tombstone.getStatus()).isEqualTo(ProfileStatus.DELETED);
		assertThat(tombstone.getDeletedAt()).isNotNull();
		assertThat(tombstone.getUser()).isNull();
		assertThat(tombstone.getBio()).isNull();
		assertThat(tombstone.getTelegram()).isNull();
		assertThat(tombstone.getFaculty()).isNull();
		assertThat(tombstone.getStudyProgram()).isNull();
		assertThat(tombstone.getCourse()).isNull();

		assertThat(profileSkillRepository.findAllByProfileId(account.profileId())).isEmpty();
		assertThat(profileInterestRepository.findAllByProfileId(account.profileId())).isEmpty();
		assertThat(profileGoalRepository.findAllByProfileId(account.profileId())).isEmpty();
		assertThat(profilePhotoRepository.findAllByProfileIdOrderByPositionAsc(
				account.profileId()
		)).isEmpty();
		verify(objectStorageService).deleteObject(
				"profiles/" + account.profileId() + "/account-delete-photo"
		);
		assertThat(impressionRepository.findAll()).noneMatch(impression ->
				involves(impression.getSourceProfileId(), impression.getTargetProfileId(), account)
		);
		assertThat(interactionEventRepository.findAll()).noneMatch(event ->
				involves(event.getSourceProfileId(), event.getTargetProfileId(), account)
		);
		assertThat(blockRepository.findAll()).noneMatch(block ->
				involves(block.getBlockerProfileId(), block.getBlockedProfileId(), account)
		);
		assertThat(skillSuggestionRepository.findBySuggestedByIdOrderByCreatedAtDesc(
				account.userId()
		)).isEmpty();
		assertThat(interestSuggestionRepository.findBySuggestedByIdOrderByCreatedAtDesc(
				account.userId()
		)).isEmpty();
		assertThat(skillSuggestionRepository.findBySuggestedByIdOrderByCreatedAtDesc(
				survivor.userId()
		)).allMatch(suggestion -> suggestion.getReviewedBy() == null);
		assertThat(interestSuggestionRepository.findBySuggestedByIdOrderByCreatedAtDesc(
				survivor.userId()
		)).allMatch(suggestion -> suggestion.getReviewedBy() == null);

		assertThat(authSessionRepository.existsByUserId(account.userId())).isFalse();
		assertThat(emailVerificationTokenRepository.existsByUserId(account.userId())).isFalse();
		assertThat(passwordResetTokenRepository.existsByUserId(account.userId())).isFalse();
	}

	private void assertSharedHistoryPreserved(TestAccount deleted, TestAccount survivor) {
		assertThat(voteRepository.findBySourceProfileId(deleted.profileId())).hasSize(1);
		assertThat(voteRepository.findBySourceProfileId(survivor.profileId())).hasSize(1);
		assertThat(matchRepository.findAll()).hasSize(1);
	}

	private void assertDeletedProfileVisibleAsTombstone(
			TestAccount deleted,
			TestAccount survivor
	) throws Exception {
		mockMvc.perform(get("/api/matches")
					.header(HttpHeaders.AUTHORIZATION, bearer(survivor.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].matchedProfile.id")
						.value(deleted.profileId().toString()))
				.andExpect(jsonPath("$.content[0].matchedProfile.displayName")
						.value("Deleted account"))
				.andExpect(jsonPath("$.content[0].matchedProfile.deleted").value(true))
				.andExpect(jsonPath("$.content[0].matchedProfile.telegram").doesNotExist());

		mockMvc.perform(get("/api/votes")
					.header(HttpHeaders.AUTHORIZATION, bearer(survivor.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].targetProfileId")
						.value(deleted.profileId().toString()))
				.andExpect(jsonPath("$.content[0].targetDeleted").value(true));

		mockMvc.perform(get("/api/profiles")
					.header(HttpHeaders.AUTHORIZATION, bearer(survivor.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].id", not(hasItem(
						deleted.profileId().toString()
				))));
	}

	private void assertInteractionsWithDeletedProfileAreRejected(
			TestAccount deleted,
			TestAccount survivor
	) throws Exception {
		vote(survivor, deleted.profileId())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(
						"Cannot interact with deleted profile: " + deleted.profileId()
				));
		mockMvc.perform(post("/api/blocks/{profileId}", deleted.profileId())
					.header(HttpHeaders.AUTHORIZATION, bearer(survivor.accessToken())))
				.andExpect(status().isConflict());
		mockMvc.perform(delete("/api/matches/{profileId}", deleted.profileId())
					.header(HttpHeaders.AUTHORIZATION, bearer(survivor.accessToken())))
				.andExpect(status().isConflict());
	}

	private void assertOldTokensAreRejected(TestAccount deleted) throws Exception {
		mockMvc.perform(get("/api/auth/me")
					.header(HttpHeaders.AUTHORIZATION, bearer(deleted.accessToken())))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/refresh")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(java.util.Map.of(
							"refreshToken",
							deleted.refreshToken()
					))))
				.andExpect(status().isUnauthorized());
	}

	private boolean involves(UUID firstProfileId, UUID secondProfileId, TestAccount account) {
		return account.profileId().equals(firstProfileId)
				|| account.profileId().equals(secondProfileId);
	}

	private TestAccount registerAccount(String email) throws Exception {
		registerOnly(email).andExpect(status().isCreated());
		String verificationToken = LocalEmailVerificationSender.takeToken(email)
				.orElseThrow();
		mockMvc.perform(post("/api/auth/email/verify")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(java.util.Map.of(
							"token",
							verificationToken
					))))
				.andExpect(status().isNoContent());

		String loginResponse = mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "email": "%s",
							  "password": "%s"
							}
							""".formatted(email, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode loginJson = objectMapper.readTree(loginResponse);
		UUID userId = UUID.fromString(loginJson.path("user").path("id").asText());
		String accessToken = loginJson.path("accessToken").asText();
		String refreshToken = loginJson.path("refreshToken").asText();

		String profileResponse = mockMvc.perform(post("/api/profiles")
					.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "displayName": "Account deletion test",
							  "bio": "Private bio",
							  "telegram": "@private_account",
							  "faculty": "FICT",
							  "studyProgram": "Software Engineering",
							  "course": 2
							}
							"""))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		UUID profileId = UUID.fromString(
				objectMapper.readTree(profileResponse).path("id").asText()
		);
		return new TestAccount(
				email,
				userId,
				profileId,
				accessToken,
				refreshToken
		);
	}

	private org.springframework.test.web.servlet.ResultActions registerOnly(String email)
			throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "%s"
						}
						""".formatted(email, PASSWORD)));
	}

	private org.springframework.test.web.servlet.ResultActions vote(
			TestAccount source,
			UUID targetProfileId
	) throws Exception {
		return mockMvc.perform(post("/api/votes/{profileId}", targetProfileId)
				.header(HttpHeaders.AUTHORIZATION, bearer(source.accessToken()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "action": "LIKE"
						}
						"""));
	}

	private org.springframework.test.web.servlet.ResultActions deleteAccount(
			TestAccount account,
			String currentPassword
	) throws Exception {
		return mockMvc.perform(delete("/api/users/me")
				.header(HttpHeaders.AUTHORIZATION, bearer(account.accessToken()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(deleteAccountBody(currentPassword)));
	}

	private void requestPasswordReset(String email) throws Exception {
		mockMvc.perform(post("/api/auth/password/forgot")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(java.util.Map.of("email", email))))
				.andExpect(status().isAccepted());
		assertThat(LocalPasswordResetSender.takeToken(email)).isPresent();
	}

	private String deleteAccountBody(String currentPassword) throws Exception {
		return objectMapper.writeValueAsString(java.util.Map.of(
				"currentPassword",
				currentPassword
		));
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private record TestAccount(
			String email,
			UUID userId,
			UUID profileId,
			String accessToken,
			String refreshToken
	) {
	}
}
