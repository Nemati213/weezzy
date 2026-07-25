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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void recommendationsUseWeightedSkillsInterestsAndGoals() throws Exception {
		String source = createProfile("Recommendation Source");
		String alice = createProfile("Recommendation Alice");
		String timur = createProfile("Recommendation Timur");
		String diana = createProfile("Recommendation Diana");
		String bob = createProfile("Recommendation Bob");

		String java = createSkill("Recommendation Java");
		String spring = createSkill("Recommendation Spring");
		String python = createSkill("Recommendation Python");
		String startups = createInterest("Recommendation Startups");
		String hackathons = createInterest("Recommendation Hackathons");
		String teamSearch = createGoal("RECOMMENDATION_TEAM_SEARCH", "Recommendation Team Search");

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

		mockMvc.perform(get(source + "/recommendations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].profile.displayName").value("Recommendation Alice"))
				.andExpect(jsonPath("$[0].score").value(7))
				.andExpect(jsonPath("$[0].matchedSkills[0]").value("Recommendation Java"))
				.andExpect(jsonPath("$[0].matchedInterests[0]").value("Recommendation Hackathons"))
				.andExpect(jsonPath("$[0].matchedInterests[1]").value("Recommendation Startups"))
				.andExpect(jsonPath("$[0].matchedGoals").isEmpty())
				.andExpect(jsonPath("$[1].profile.displayName").value("Recommendation Timur"))
				.andExpect(jsonPath("$[1].score").value(6))
				.andExpect(jsonPath("$[1].matchedInterests").isEmpty())
				.andExpect(jsonPath("$[1].matchedGoals").isEmpty())
				.andExpect(jsonPath("$[2].profile.displayName").value("Recommendation Diana"))
				.andExpect(jsonPath("$[2].score").value(5))
				.andExpect(jsonPath("$[2].matchedGoals[0]").value("Recommendation Team Search"))
				.andExpect(content().string(not(containsString("Recommendation Bob"))))
				.andExpect(content().string(not(containsString("Recommendation Source"))));
	}

	@Test
	void recommendationsWorkWhenProfileHasOnlyInterests() throws Exception {
		String source = createProfile("Recommendation Only Interests Source");
		String candidate = createProfile("Recommendation Only Interests Candidate");
		String interest = createInterest("Recommendation Only Interests Open Source");
		addInterest(source, interest);
		addInterest(candidate, interest);

		mockMvc.perform(get(source + "/recommendations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].profile.displayName")
						.value("Recommendation Only Interests Candidate"))
				.andExpect(jsonPath("$[0].score").value(2))
				.andExpect(jsonPath("$[0].matchedSkills").isEmpty())
				.andExpect(jsonPath("$[0].matchedInterests[0]")
						.value("Recommendation Only Interests Open Source"))
				.andExpect(jsonPath("$[0].matchedGoals").isEmpty());
	}

	@Test
	void recommendationsWorkWhenProfileHasOnlyGoals() throws Exception {
		String source = createProfile("Recommendation Only Goals Source");
		String candidate = createProfile("Recommendation Only Goals Candidate");
		String goal = createGoal("RECOMMENDATION_ONLY_GOALS", "Recommendation Only Goals Team");
		addGoal(source, goal);
		addGoal(candidate, goal);

		mockMvc.perform(get(source + "/recommendations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].profile.displayName")
						.value("Recommendation Only Goals Candidate"))
				.andExpect(jsonPath("$[0].score").value(5))
				.andExpect(jsonPath("$[0].matchedSkills").isEmpty())
				.andExpect(jsonPath("$[0].matchedInterests").isEmpty())
				.andExpect(jsonPath("$[0].matchedGoals[0]")
						.value("Recommendation Only Goals Team"));
	}

	@Test
	void recommendationsReturnEmptyListWhenProfileHasNoSignals() throws Exception {
		String source = createProfile("Recommendation No Signals Source");
		createProfile("Recommendation No Signals Candidate");

		mockMvc.perform(get(source + "/recommendations"))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
	}

	@Test
	void recommendationsReturnNotFoundForMissingProfile() throws Exception {
		mockMvc.perform(get("/api/profiles/00000000-0000-0000-0000-000000000000/recommendations"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("Profile not found: 00000000-0000-0000-0000-000000000000"))
				.andExpect(jsonPath("$.path").value("/api/profiles/00000000-0000-0000-0000-000000000000/recommendations"));
	}

	private String createProfile(String displayName) throws Exception {
		return mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "%s",
								  "bio": "Created for recommendation tests",
								  "telegram": "@recommendation_test",
								  "faculty": "FICT",
								  "studyProgram": "Software Engineering",
								  "course": 2
								}
								""".formatted(displayName)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");
	}

	private String createSkill(String name) throws Exception {
		return mockMvc.perform(post("/api/skills")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "Created for recommendation tests"
								}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");
	}

	private String createInterest(String name) throws Exception {
		return mockMvc.perform(post("/api/interests")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "Created for recommendation tests"
								}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");
	}

	private String createGoal(String code, String name) throws Exception {
		return mockMvc.perform(post("/api/goals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "%s",
								  "name": "%s",
								  "description": "Created for recommendation tests"
								}
								""".formatted(code, name)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");
	}

	private void addSkill(String profileLocation, String skillLocation) throws Exception {
		mockMvc.perform(post(profileLocation + "/skills/" + idFromLocation(skillLocation)))
				.andExpect(status().isCreated());
	}

	private void addInterest(String profileLocation, String interestLocation) throws Exception {
		mockMvc.perform(post(profileLocation + "/interests/" + idFromLocation(interestLocation)))
				.andExpect(status().isCreated());
	}

	private void addGoal(String profileLocation, String goalLocation) throws Exception {
		mockMvc.perform(post(profileLocation + "/goals/" + idFromLocation(goalLocation)))
				.andExpect(status().isCreated());
	}

	private String idFromLocation(String location) {
		return location.substring(location.lastIndexOf('/') + 1);
	}
}
