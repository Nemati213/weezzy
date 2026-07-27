package ru.itmo.nemat.weezzy.recommendation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import tools.jackson.databind.ObjectMapper;

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
				.andExpect(jsonPath("$[0].profile.displayName").value("Recommendation Alice"))
				.andExpect(jsonPath("$[0].score").value(7))
				.andExpect(jsonPath("$[1].profile.displayName").value("Recommendation Timur"))
				.andExpect(jsonPath("$[1].score").value(6))
				.andExpect(jsonPath("$[2].profile.displayName").value("Recommendation Diana"))
				.andExpect(jsonPath("$[2].score").value(5))
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
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void recommendationsReturnEmptyListWhenCurrentProfileHasNoSignals() throws Exception {
		TestProfile source = createProfile("Recommendation No Signals Source");
		TestProfile candidate = createProfile("Recommendation No Signals Candidate");
		activateProfile(candidate);

		mockMvc.perform(source.owner().authorize(get("/api/recommendations")))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
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
		return createCatalogItem("/api/skills", """
				{
				  "name": "%s",
				  "description": "Created for recommendation tests"
				}
				""".formatted(name));
	}

	private String createInterest(String name) throws Exception {
		return createCatalogItem("/api/interests", """
				{
				  "name": "%s",
				  "description": "Created for recommendation tests"
				}
				""".formatted(name));
	}

	private String createGoal(String code, String name) throws Exception {
		return createCatalogItem("/api/goals", """
				{
				  "code": "%s",
				  "name": "%s",
				  "description": "Created for recommendation tests"
				}
				""".formatted(code, name));
	}

	private String createCatalogItem(String url, String body) throws Exception {
		return mockMvc.perform(post(url)
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

	private String idFromLocation(String location) {
		return location.substring(location.lastIndexOf('/') + 1);
	}
}
