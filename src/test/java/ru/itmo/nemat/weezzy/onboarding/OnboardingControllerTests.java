package ru.itmo.nemat.weezzy.onboarding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.security.JwtService;
import ru.itmo.nemat.weezzy.storage.ObjectStorageService;
import ru.itmo.nemat.weezzy.storage.dto.PresignedDownload;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.user.UserRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.itmo.nemat.weezzy.support.ProfilePhotoTestData.insertReadyPhoto;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OnboardingControllerTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

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
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private ObjectStorageService storageService;

	@BeforeEach
	void configureStorage() {
		when(storageService.createDownload(anyString())).thenReturn(
				new PresignedDownload(
						"http://storage/download",
						LocalDateTime.now().plusMinutes(15)
				)
		);
	}

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void onboardingStartsAtZeroBeforeProfileCreation() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(get("/api/onboarding/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.progress").value(0))
				.andExpect(jsonPath("$.activationAllowed").value(false))
				.andExpect(jsonPath("$.missingSteps", containsInAnyOrder(
						"PROFILE_DETAILS",
						"SKILLS",
						"INTERESTS",
						"GOALS",
						"PHOTOS",
						"ACTIVATION"
				)));
	}

	@Test
	void onboardingProgressAdvancesAsRequiredStepsAreCompleted() throws Exception {
		TestProfile profile = createProfile("Onboarding Progress");
		CatalogItems items = createCatalogItems();

		assertProgress(profile, 16, false, "SKILLS", "INTERESTS", "GOALS", "PHOTOS", "ACTIVATION");
		addSkill(profile, items.skillId());
		assertProgress(profile, 33, false, "INTERESTS", "GOALS", "PHOTOS", "ACTIVATION");
		addInterest(profile, items.interestId());
		assertProgress(profile, 50, false, "GOALS", "PHOTOS", "ACTIVATION");
		addGoal(profile, items.goalId());
		assertProgress(profile, 66, false, "PHOTOS", "ACTIVATION");
		insertReadyPhoto(jdbcTemplate, UUID.fromString(profile.id()));
		assertProgress(profile, 83, true, "ACTIVATION");
	}

	@Test
	void activationIsRejectedUntilAllRequiredStepsAreComplete() throws Exception {
		TestProfile profile = createProfile("Onboarding Rejected Activation");

		updateStatus(profile, "ACTIVE")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString(
						"Missing steps: [SKILLS, INTERESTS, GOALS, PHOTOS]"
				)));

		mockMvc.perform(profile.owner().authorize(get("/api/profiles/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"));
	}

	@Test
	void completedProfileCanActivateHideAndActivateAgain() throws Exception {
		TestProfile profile = createProfile("Onboarding Status Transitions");
		completeRequiredSteps(profile, createCatalogItems());

		updateStatus(profile, "ACTIVE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
		assertProgress(profile, 100, true);

		updateStatus(profile, "HIDDEN")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("HIDDEN"));
		assertProgress(profile, 83, true, "ACTIVATION");

		updateStatus(profile, "ACTIVE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
		assertProgress(profile, 100, true);
	}

	@Test
	void removingLastRequiredSignalDemotesProfileUntilReactivation() throws Exception {
		TestProfile profile = createProfile("Onboarding Repeat");
		CatalogItems items = createCatalogItems();
		completeRequiredSteps(profile, items);
		updateStatus(profile, "ACTIVE").andExpect(status().isOk());

		mockMvc.perform(profile.owner().authorize(delete(
				"/api/profiles/me/skills/" + items.skillId()
		)))
				.andExpect(status().isNoContent());
		assertProgress(profile, 66, false, "SKILLS", "ACTIVATION");

		addSkill(profile, items.skillId());
		assertProgress(profile, 83, true, "ACTIVATION");
		updateStatus(profile, "ACTIVE").andExpect(status().isOk());
		assertProgress(profile, 100, true);
	}

	@Test
	void clearingRequiredDetailsDemotesActiveProfile() throws Exception {
		TestProfile profile = createProfile("Onboarding Details");
		completeRequiredSteps(profile, createCatalogItems());
		updateStatus(profile, "ACTIVE").andExpect(status().isOk());

		mockMvc.perform(profile.owner().authorize(patch("/api/profiles/me"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "telegram": ""
							}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"));
		assertProgress(profile, 66, false, "PROFILE_DETAILS", "ACTIVATION");
	}

	@Test
	void deletingLastGoalFromCatalogDemotesAffectedProfile() throws Exception {
		TestProfile profile = createProfile("Onboarding Catalog Delete");
		CatalogItems items = createCatalogItems();
		completeRequiredSteps(profile, items);
		updateStatus(profile, "ACTIVE").andExpect(status().isOk());
		AuthenticatedTestUser admin = registerAdmin();

		mockMvc.perform(admin.authorize(delete("/api/goals/" + items.goalId())))
				.andExpect(status().isNoContent());

		assertProgress(profile, 66, false, "GOALS", "ACTIVATION");
	}

	@Test
	void deletingLastReadyPhotoDemotesActiveProfile() throws Exception {
		TestProfile profile = createProfile("Onboarding Photo Removal");
		completeRequiredSteps(profile, createCatalogItems());
		updateStatus(profile, "ACTIVE").andExpect(status().isOk());
		UUID photoId = jdbcTemplate.queryForObject(
				"SELECT id FROM profile_photos WHERE profile_id = ?",
				UUID.class,
				UUID.fromString(profile.id())
		);

		mockMvc.perform(profile.owner().authorize(delete(
				"/api/profiles/me/photos/" + photoId
		)))
				.andExpect(status().isNoContent());

		assertProgress(profile, 66, false, "PHOTOS", "ACTIVATION");
	}

	@Test
	void onboardingRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/onboarding/me"))
				.andExpect(status().isUnauthorized());
	}

	private TestProfile createProfile(String displayName) throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile(displayName);
	}

	private CatalogItems createCatalogItems() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		String suffix = UUID.randomUUID().toString();
		String codeSuffix = suffix.replace("-", "").toUpperCase();

		return new CatalogItems(
				idFromLocation(createCatalogItem(admin, "/api/skills", """
						{
						  "name": "Onboarding skill %s"
						}
						""".formatted(suffix))),
				idFromLocation(createCatalogItem(admin, "/api/interests", """
						{
						  "name": "Onboarding interest %s"
						}
						""".formatted(suffix))),
				idFromLocation(createCatalogItem(admin, "/api/goals", """
						{
						  "code": "ONBOARDING_%s",
						  "name": "Onboarding goal %s"
						}
						""".formatted(codeSuffix, suffix)))
		);
	}

	private String createCatalogItem(
			AuthenticatedTestUser admin,
			String url,
			String body
	) throws Exception {
		return mockMvc.perform(admin.authorize(post(url))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");
	}

	private AuthenticatedTestUser registerAdmin() throws Exception {
		return AuthenticatedTestUser.registerAdmin(
				mockMvc,
				objectMapper,
				userRepository,
				jwtService
		);
	}

	private void completeRequiredSteps(TestProfile profile, CatalogItems items)
			throws Exception {
		addSkill(profile, items.skillId());
		addInterest(profile, items.interestId());
		addGoal(profile, items.goalId());
		insertReadyPhoto(jdbcTemplate, UUID.fromString(profile.id()));
	}

	private void addSkill(TestProfile profile, String skillId) throws Exception {
		mockMvc.perform(profile.owner().authorize(post(
				"/api/profiles/me/skills/" + skillId
		)))
				.andExpect(status().isCreated());
	}

	private void addInterest(TestProfile profile, String interestId) throws Exception {
		mockMvc.perform(profile.owner().authorize(post(
				"/api/profiles/me/interests/" + interestId
		)))
				.andExpect(status().isCreated());
	}

	private void addGoal(TestProfile profile, String goalId) throws Exception {
		mockMvc.perform(profile.owner().authorize(post(
				"/api/profiles/me/goals/" + goalId
		)))
				.andExpect(status().isCreated());
	}

	private org.springframework.test.web.servlet.ResultActions updateStatus(
			TestProfile profile,
			String statusValue
	) throws Exception {
		return mockMvc.perform(profile.owner().authorize(patch("/api/profiles/me"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "status": "%s"
						}
						""".formatted(statusValue)));
	}

	private void assertProgress(
			TestProfile profile,
			int progress,
			boolean activationAllowed,
			String... missingSteps
	) throws Exception {
		mockMvc.perform(profile.owner().authorize(get("/api/onboarding/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profileId").value(profile.id()))
				.andExpect(jsonPath("$.progress").value(progress))
				.andExpect(jsonPath("$.activationAllowed").value(activationAllowed))
				.andExpect(jsonPath("$.missingSteps", containsInAnyOrder(missingSteps)));
	}

	private String idFromLocation(String location) {
		return location.substring(location.lastIndexOf('/') + 1);
	}

	private record CatalogItems(String skillId, String interestId, String goalId) {
	}
}
