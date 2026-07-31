package ru.itmo.nemat.weezzy.recommendation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.security.JwtService;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.user.UserRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RecommendationControllerTests {

	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

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

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void recommendationsUseCurrentProfileAndWeightedSignals() throws Exception {
		TestProfile source = createProfile("Recommendation Source");
		TestProfile alice = createProfile("Recommendation Alice");
		TestProfile timur = createProfile("Recommendation Timur");
		TestProfile diana = createProfile("Recommendation Diana");
		TestProfile bob = createProfile("Recommendation Bob");

		String java = idFromLocation(createSkill("Recommendation Java"));
		String spring = idFromLocation(createSkill("Recommendation Spring"));
		String python = idFromLocation(createSkill("Recommendation Python"));
		String startups = idFromLocation(createInterest("Recommendation Startups"));
		String hackathons = idFromLocation(createInterest("Recommendation Hackathons"));
		String teamSearch = idFromLocation(createGoal(
				"RECOMMENDATION_TEAM_SEARCH",
				"Recommendation Team Search"
		));

		addSkill(source, java);
		addSkill(source, spring);
		addInterest(source, startups);
		addInterest(source, hackathons);
		addGoal(source, teamSearch);

		addSkill(alice, java);
		addInterest(alice, startups);
		addInterest(alice, hackathons);
		addSkill(timur, java);
		addSkill(timur, spring);
		addGoal(diana, teamSearch);
		addSkill(bob, python);

		activateProfile(alice);
		activateProfile(timur);
		activateProfile(diana);
		activateProfile(bob);

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].profile.displayName").value("Recommendation Alice"))
				.andExpect(jsonPath("$.content[0].profile.telegram").doesNotExist())
				.andExpect(jsonPath("$.content[0].score").value(7))
				.andExpect(jsonPath("$.content[0].reason.scoreBreakdown.skills").value(3))
				.andExpect(jsonPath("$.content[0].reason.scoreBreakdown.interests").value(4))
				.andExpect(jsonPath("$.content[0].reason.scoreBreakdown.goals").value(0))
				.andExpect(jsonPath("$.content[0].reason.matchedCounts.skills").value(1))
				.andExpect(jsonPath("$.content[0].reason.matchedCounts.interests").value(2))
				.andExpect(jsonPath("$.content[0].reason.matchedCounts.goals").value(0))
				.andExpect(jsonPath("$.content[1].profile.displayName").value("Recommendation Timur"))
				.andExpect(jsonPath("$.content[1].score").value(6))
				.andExpect(jsonPath("$.content[2].profile.displayName").value("Recommendation Diana"))
				.andExpect(jsonPath("$.content[2].score").value(5))
				.andExpect(jsonPath("$.nextCursor").doesNotExist())
				.andExpect(content().string(not(containsString("Recommendation Bob"))))
				.andExpect(content().string(not(containsString("Recommendation Source"))));
	}

	@Test
	void recommendationsRespectLimitParameter() throws Exception {
		TestProfile source = createProfile("Recommendation Limit Source");
		TestProfile first = createProfile("Recommendation Limit First");
		TestProfile second = createProfile("Recommendation Limit Second");
		String goal = idFromLocation(createGoal("RECOMMENDATION_LIMIT_GOAL", "Recommendation Limit Goal"));
		addGoal(source, goal);
		addGoal(first, goal);
		addGoal(second, goal);
		activateProfile(first);
		activateProfile(second);

		mockMvc.perform(source.owner().authorize(get("/api/recommendations?limit=1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.nextCursor").isNotEmpty());
	}

	@Test
	void recommendationsContinueAfterCursor() throws Exception {
		TestProfile source = createProfile("Recommendation Cursor Source");
		TestProfile first = createProfile("Recommendation Cursor First");
		TestProfile second = createProfile("Recommendation Cursor Second");
		String skill = idFromLocation(createSkill("Recommendation Cursor Skill"));
		String goal = idFromLocation(createGoal("RECOMMENDATION_CURSOR_GOAL", "Recommendation Cursor Goal"));
		addSkill(source, skill);
		addGoal(source, goal);
		addSkill(first, skill);
		addGoal(first, goal);
		addGoal(second, goal);
		activateProfile(first);
		activateProfile(second);

		String firstPageBody = mockMvc.perform(source.owner().authorize(get("/api/recommendations")
						.param("limit", "1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].profile.displayName")
						.value("Recommendation Cursor First"))
				.andExpect(jsonPath("$.nextCursor").isNotEmpty())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String nextCursor = objectMapper.readTree(firstPageBody).path("nextCursor").asText();

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")
						.param("limit", "1")
						.param("cursor", nextCursor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].profile.displayName")
						.value("Recommendation Cursor Second"))
				.andExpect(jsonPath("$.nextCursor").doesNotExist())
				.andExpect(content().string(not(containsString("Recommendation Cursor First"))));
	}

	@Test
	void recommendationsDoNotRepeatRecentlyShownCandidate() throws Exception {
		TestProfile source = createProfile("Recommendation Impression Source");
		TestProfile candidate = createProfile("Recommendation Impression Candidate");
		String goal = idFromLocation(createGoal(
				"RECOMMENDATION_IMPRESSION_GOAL",
				"Recommendation Impression Goal"
		));
		addGoal(source, goal);
		addGoal(candidate, goal);
		activateProfile(candidate);

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].profile.displayName")
						.value("Recommendation Impression Candidate"))
				.andExpect(jsonPath("$.nextCursor").doesNotExist());

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void recommendationsShowCandidateAgainAfterImpressionCooldown() throws Exception {
		TestProfile source = createProfile("Recommendation Cooldown Source");
		TestProfile candidate = createProfile("Recommendation Cooldown Candidate");
		String goal = idFromLocation(createGoal(
				"RECOMMENDATION_COOLDOWN_GOAL",
				"Recommendation Cooldown Goal"
		));
		addGoal(source, goal);
		addGoal(candidate, goal);
		activateProfile(candidate);

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Recommendation Cooldown Candidate")));

		jdbcTemplate.update("""
						UPDATE profile_recommendation_impressions
						SET shown_at = ?
						WHERE source_profile_id = ? AND target_profile_id = ?
						""",
				LocalDateTime.now().minusDays(8),
				UUID.fromString(source.id()),
				UUID.fromString(candidate.id())
		);

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Recommendation Cooldown Candidate")));
	}

	@Test
	void impressionsAreScopedToSourceProfile() throws Exception {
		TestProfile firstSource = createProfile("Recommendation Scope First Source");
		TestProfile secondSource = createProfile("Recommendation Scope Second Source");
		TestProfile candidate = createProfile("Recommendation Scope Candidate");
		String goal = idFromLocation(createGoal(
				"RECOMMENDATION_SCOPE_GOAL",
				"Recommendation Scope Goal"
		));
		addGoal(firstSource, goal);
		addGoal(secondSource, goal);
		addGoal(candidate, goal);
		activateProfile(candidate);

		mockMvc.perform(firstSource.owner().authorize(get("/api/recommendations")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Recommendation Scope Candidate")));

		mockMvc.perform(secondSource.owner().authorize(get("/api/recommendations")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Recommendation Scope Candidate")));
	}

	@Test
	void recommendationsFilterByProfileFields() throws Exception {
		TestProfile source = createProfile("Recommendation Fields Source");
		TestProfile matching = createProfile("Recommendation Fields Matching");
		TestProfile wrongFaculty = createProfile("Recommendation Fields Faculty");
		TestProfile wrongProgram = createProfile("Recommendation Fields Program");
		TestProfile wrongCourse = createProfile("Recommendation Fields Course");
		String goal = idFromLocation(createGoal(
				"RECOMMENDATION_FIELDS_GOAL",
				"Recommendation Fields Goal"
		));
		addGoal(source, goal);
		addGoal(matching, goal);
		addGoal(wrongFaculty, goal);
		addGoal(wrongProgram, goal);
		addGoal(wrongCourse, goal);
		updateProfileDetails(matching, "FICT", "Software Engineering", 3);
		updateProfileDetails(wrongFaculty, "CT", "Software Engineering", 3);
		updateProfileDetails(wrongProgram, "FICT", "Applied Mathematics", 3);
		updateProfileDetails(wrongCourse, "FICT", "Software Engineering", 4);

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")
						.param("faculty", "FICT")
						.param("studyProgram", "Software Engineering")
						.param("courses", "2,3")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].profile.displayName")
						.value("Recommendation Fields Matching"))
				.andExpect(content().string(not(containsString(
						"Recommendation Fields Faculty"
				))))
				.andExpect(content().string(not(containsString(
						"Recommendation Fields Program"
				))))
				.andExpect(content().string(not(containsString(
						"Recommendation Fields Course"
				))));
	}

	@Test
	void recommendationsCombineSignalFilterGroupsWithAnd() throws Exception {
		TestProfile source = createProfile("Recommendation Signals Source");
		TestProfile matching = createProfile("Recommendation Signals Matching");
		TestProfile missingSkill = createProfile("Recommendation Signals No Skill");
		TestProfile missingInterest = createProfile("Recommendation Signals No Interest");
		TestProfile missingGoal = createProfile("Recommendation Signals No Goal");
		String skill = idFromLocation(createSkill("Recommendation Signals Java"));
		String interest = idFromLocation(createInterest("Recommendation Signals Startups"));
		String goal = idFromLocation(createGoal(
				"RECOMMENDATION_SIGNALS_GOAL",
				"Recommendation Signals Goal"
		));

		addSkill(source, skill);
		addInterest(source, interest);
		addGoal(source, goal);
		addSkill(matching, skill);
		addInterest(matching, interest);
		addGoal(matching, goal);
		addInterest(missingSkill, interest);
		addGoal(missingSkill, goal);
		addSkill(missingInterest, skill);
		addGoal(missingInterest, goal);
		addSkill(missingGoal, skill);
		addInterest(missingGoal, interest);
		activateProfile(matching);
		activateProfile(missingSkill);
		activateProfile(missingInterest);
		activateProfile(missingGoal);

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")
						.param("skillIds", skill)
						.param("interestIds", interest)
						.param("goalIds", goal)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].profile.displayName")
						.value("Recommendation Signals Matching"));
	}

	@Test
	void recommendationsTreatMultipleSkillIdsAsAny() throws Exception {
		TestProfile source = createProfile("Recommendation Any Skill Source");
		TestProfile javaCandidate = createProfile("Recommendation Any Skill Java");
		TestProfile springCandidate = createProfile("Recommendation Any Skill Spring");
		TestProfile pythonCandidate = createProfile("Recommendation Any Skill Python");
		String java = idFromLocation(createSkill("Recommendation Any Java"));
		String spring = idFromLocation(createSkill("Recommendation Any Spring"));
		String python = idFromLocation(createSkill("Recommendation Any Python"));
		String goal = idFromLocation(createGoal(
				"RECOMMENDATION_ANY_SKILL_GOAL",
				"Recommendation Any Skill Goal"
		));

		addSkill(source, java);
		addSkill(source, spring);
		addGoal(source, goal);
		addSkill(javaCandidate, java);
		addSkill(springCandidate, spring);
		addSkill(pythonCandidate, python);
		addGoal(javaCandidate, goal);
		addGoal(springCandidate, goal);
		addGoal(pythonCandidate, goal);
		activateProfile(javaCandidate);
		activateProfile(springCandidate);
		activateProfile(pythonCandidate);

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")
						.param("skillIds", java + "," + spring)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(content().string(containsString("Recommendation Any Skill Java")))
				.andExpect(content().string(containsString("Recommendation Any Skill Spring")))
				.andExpect(content().string(not(containsString(
						"Recommendation Any Skill Python"
				))));
	}

	@Test
	void recommendationsRejectCourseOutsideSupportedRange() throws Exception {
		TestProfile source = createProfile("Recommendation Invalid Course Source");

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")
						.param("courses", "0")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("courses")));
	}

	@Test
	void recommendationsReturnEmptyListWhenCurrentProfileHasNoSignals() throws Exception {
		TestProfile source = createProfile("Recommendation No Signals Source");
		TestProfile candidate = createProfile("Recommendation No Signals Candidate");
		activateProfile(candidate);

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.nextCursor").doesNotExist());
	}

	@Test
	void recommendationsSkipDraftAndHiddenCandidates() throws Exception {
		TestProfile source = createProfile("Recommendation Status Source");
		TestProfile active = createProfile("Recommendation Active Candidate");
		TestProfile draft = createProfile("Recommendation Draft Candidate");
		TestProfile hidden = createProfile("Recommendation Hidden Candidate");
		String goal = idFromLocation(createGoal("RECOMMENDATION_STATUS_GOAL", "Recommendation Status Goal"));
		addGoal(source, goal);
		addGoal(active, goal);
		addGoal(draft, goal);
		addGoal(hidden, goal);
		activateProfile(active);
		updateProfileStatus(hidden, "HIDDEN");

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Recommendation Active Candidate")))
				.andExpect(content().string(not(containsString("Recommendation Draft Candidate"))))
				.andExpect(content().string(not(containsString("Recommendation Hidden Candidate"))));
	}

	@Test
	void recommendationsSkipCandidateAfterVote() throws Exception {
		TestProfile source = createProfile("Recommendation Vote Source");
		TestProfile candidate = createProfile("Recommendation Vote Candidate");
		String goal = idFromLocation(createGoal("RECOMMENDATION_VOTE_GOAL", "Recommendation Vote Goal"));
		addGoal(source, goal);
		addGoal(candidate, goal);
		activateProfile(candidate);

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")))
				.andExpect(content().string(containsString("Recommendation Vote Candidate")));

		mockMvc.perform(source.owner().authorize(post("/api/votes/" + candidate.id()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "action": "PASS"
								}
								"""))
				.andExpect(status().isOk());

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Recommendation Vote Candidate"))));
	}

	@Test
	void recommendationsSkipBlockedProfilesInBothDirections() throws Exception {
		TestProfile source = createProfile("Recommendation Block Source");
		TestProfile blockedBySource = createProfile(
				"Recommendation Blocked By Source"
		);
		TestProfile sourceBlockedByCandidate = createProfile(
				"Recommendation Source Blocked By Candidate"
		);
		String goal = idFromLocation(createGoal(
				"RECOMMENDATION_BLOCK_GOAL",
				"Recommendation Block Goal"
		));
		addGoal(source, goal);
		addGoal(blockedBySource, goal);
		addGoal(sourceBlockedByCandidate, goal);
		activateProfile(blockedBySource);
		activateProfile(sourceBlockedByCandidate);
		mockMvc.perform(source.owner().authorize(
				post("/api/blocks/" + blockedBySource.id())
		)).andExpect(status().isOk());
		mockMvc.perform(sourceBlockedByCandidate.owner().authorize(
				post("/api/blocks/" + source.id())
		)).andExpect(status().isOk());

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(content().string(not(containsString(
						"Recommendation Blocked By Source"
				))))
				.andExpect(content().string(not(containsString(
						"Recommendation Source Blocked By Candidate"
				))));
	}

	@Test
	void recommendationsRejectInvalidCursor() throws Exception {
		TestProfile source = createProfile("Recommendation Invalid Cursor Source");

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")
						.param("cursor", "not-a-cursor")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid recommendation cursor"));
	}

	@Test
	void recommendationsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/recommendations"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void recommendationsReturnNotFoundWhenCurrentUserHasNoProfile() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(get("/api/recommendations")))
				.andExpect(status().isNotFound());
	}

	private TestProfile createProfile(String displayName) throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile(displayName);
	}

	private String createSkill(String name) throws Exception {
		return createAdminCatalogItem("/api/skills", """
				{
				  "name": "%s",
				  "description": "Created for recommendation tests"
				}
				""".formatted(name));
	}

	private String createInterest(String name) throws Exception {
		return createAdminCatalogItem("/api/interests", """
				{
				  "name": "%s",
				  "description": "Created for recommendation tests"
				}
				""".formatted(name));
	}

	private String createGoal(String code, String name) throws Exception {
		return createAdminCatalogItem("/api/goals", """
				{
				  "code": "%s",
				  "name": "%s",
				  "description": "Created for recommendation tests"
				}
				""".formatted(code, name));
	}

	private String createAdminCatalogItem(String url, String body) throws Exception {
		AuthenticatedTestUser admin = AuthenticatedTestUser.registerAdmin(
				mockMvc,
				objectMapper,
				userRepository,
				jwtService
		);

		return mockMvc.perform(admin.authorize(post(url))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");
	}

	private void addSkill(TestProfile profile, String skillId) throws Exception {
		mockMvc.perform(profile.owner().authorize(post("/api/profiles/me/skills/" + skillId)))
				.andExpect(status().isCreated());
	}

	private void addInterest(TestProfile profile, String interestId) throws Exception {
		mockMvc.perform(profile.owner().authorize(post("/api/profiles/me/interests/" + interestId)))
				.andExpect(status().isCreated());
	}

	private void addGoal(TestProfile profile, String goalId) throws Exception {
		mockMvc.perform(profile.owner().authorize(post("/api/profiles/me/goals/" + goalId)))
				.andExpect(status().isCreated());
	}

	private void activateProfile(TestProfile profile) throws Exception {
		completeOnboardingSignals(profile);
		updateProfileStatus(profile, "ACTIVE");
	}

	private void updateProfileStatus(TestProfile profile, String statusValue) throws Exception {
		mockMvc.perform(profile.owner().authorize(patch("/api/profiles/me"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "status": "%s"
								}
								""".formatted(statusValue)))
				.andExpect(status().isOk());
	}

	private void updateProfileDetails(
			TestProfile profile,
			String faculty,
			String studyProgram,
			int course
	) throws Exception {
		completeOnboardingSignals(profile);
		mockMvc.perform(profile.owner().authorize(patch("/api/profiles/me"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "faculty": "%s",
								  "studyProgram": "%s",
								  "course": %d,
								  "status": "ACTIVE"
								}
								""".formatted(faculty, studyProgram, course)))
				.andExpect(status().isOk());
	}

	private void completeOnboardingSignals(TestProfile profile) throws Exception {
		String suffix = UUID.randomUUID().toString();
		String codeSuffix = suffix.replace("-", "").toUpperCase();
		addSkill(profile, idFromLocation(createSkill("Filler skill " + suffix)));
		addInterest(profile, idFromLocation(createInterest("Filler interest " + suffix)));
		addGoal(profile, idFromLocation(createGoal(
				"FILLER_" + codeSuffix,
				"Filler goal " + suffix
		)));
	}

	private String idFromLocation(String location) {
		return location.substring(location.lastIndexOf('/') + 1);
	}
}
