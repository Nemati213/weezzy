package ru.itmo.nemat.weezzy.profile;

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
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ProfileGoalControllerTests {

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
	void addGoalToProfileReturnsLinkedGoal() throws Exception {
		String profile = createProfile("Goal Link Profile");
		String goal = createGoal("PROFILE_GOAL_TEAM", "Profile Goal Team");

		mockMvc.perform(post(profile + "/goals/" + idFromLocation(goal)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern(profile + "/goals/.+")))
				.andExpect(jsonPath("$.code").value("PROFILE_GOAL_TEAM"))
				.andExpect(jsonPath("$.name").value("Profile Goal Team"));
	}

	@Test
	void getProfileGoalsReturnsLinkedGoals() throws Exception {
		String profile = createProfile("Goal List Profile");
		String goal = createGoal("PROFILE_GOAL_HACKATHON", "Profile Goal Hackathon");
		addGoal(profile, goal);

		mockMvc.perform(get(profile + "/goals"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("PROFILE_GOAL_HACKATHON")))
				.andExpect(content().string(containsString("Profile Goal Hackathon")));
	}

	@Test
	void addGoalToProfileRejectsDuplicateLink() throws Exception {
		String profile = createProfile("Goal Duplicate Profile");
		String goal = createGoal("PROFILE_GOAL_DUPLICATE", "Profile Goal Duplicate");
		String linkUrl = profile + "/goals/" + idFromLocation(goal);
		addGoal(profile, goal);

		mockMvc.perform(post(linkUrl))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value(containsString("Profile already has goal")));
	}

	@Test
	void removeGoalFromProfileDeletesLink() throws Exception {
		String profile = createProfile("Goal Delete Profile");
		String goal = createGoal("PROFILE_GOAL_DELETE", "Profile Goal Delete");
		String linkUrl = profile + "/goals/" + idFromLocation(goal);
		addGoal(profile, goal);

		mockMvc.perform(delete(linkUrl))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(profile + "/goals"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("PROFILE_GOAL_DELETE"))));
	}

	@Test
	void addGoalToMissingProfileReturnsNotFound() throws Exception {
		String goal = createGoal("PROFILE_GOAL_MISSING_PROFILE", "Profile Goal Missing Profile");

		mockMvc.perform(post("/api/profiles/00000000-0000-0000-0000-000000000000/goals/"
						+ idFromLocation(goal)))
				.andExpect(status().isNotFound());
	}

	@Test
	void addMissingGoalToProfileReturnsNotFound() throws Exception {
		String profile = createProfile("Missing Goal Profile");

		mockMvc.perform(post(profile + "/goals/00000000-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound());
	}

	@Test
	void removeMissingProfileGoalLinkReturnsNotFound() throws Exception {
		String profile = createProfile("Missing Goal Link Profile");
		String goal = createGoal("PROFILE_GOAL_MISSING_LINK", "Profile Goal Missing Link");

		mockMvc.perform(delete(profile + "/goals/" + idFromLocation(goal)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(containsString("Profile goal link not found")));
	}

	private String createProfile(String displayName) throws Exception {
		return mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "%s",
								  "bio": "Created for profile-goal tests",
								  "telegram": "@profile_goal_test",
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

	private String createGoal(String code, String name) throws Exception {
		return mockMvc.perform(post("/api/goals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "%s",
								  "name": "%s",
								  "description": "Created for profile-goal tests"
								}
								""".formatted(code, name)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");
	}

	private void addGoal(String profileLocation, String goalLocation) throws Exception {
		mockMvc.perform(post(profileLocation + "/goals/" + idFromLocation(goalLocation)))
				.andExpect(status().isCreated());
	}

	private String idFromLocation(String location) {
		return location.substring(location.lastIndexOf('/') + 1);
	}
}
