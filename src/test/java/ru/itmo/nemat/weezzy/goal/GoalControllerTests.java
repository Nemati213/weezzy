package ru.itmo.nemat.weezzy.goal;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GoalControllerTests {

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
	void createGoalReturnsCreatedGoal() throws Exception {
		mockMvc.perform(post("/api/goals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "OPEN_SOURCE_CREW",
								  "name": "Open Source Crew",
								  "description": "Find people to build open source projects"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern("/api/goals/.+")))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.code").value("OPEN_SOURCE_CREW"))
				.andExpect(jsonPath("$.name").value("Open Source Crew"))
				.andExpect(jsonPath("$.description").value("Find people to build open source projects"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	void createGoalTrimsName() throws Exception {
		mockMvc.perform(post("/api/goals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "PAIR_PROGRAMMING",
								  "name": "  Pair Programming  ",
								  "description": "Find someone for coding sessions"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Pair Programming"));
	}

	@Test
	void createGoalRejectsInvalidCode() throws Exception {
		mockMvc.perform(post("/api/goals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "invalid-code",
								  "name": "Invalid Goal",
								  "description": "Invalid code"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value(containsString("code")))
				.andExpect(jsonPath("$.path").value("/api/goals"));
	}

	@Test
	void createGoalRejectsDuplicateCodeIgnoringCase() throws Exception {
		createGoal("BUILD_STARTUP", "Build Startup");

		mockMvc.perform(post("/api/goals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "BUILD_STARTUP",
								  "name": "Build Another Startup",
								  "description": "Duplicate code"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value("Goal already exists: BUILD_STARTUP"));
	}

	@Test
	void createGoalRejectsDuplicateNameIgnoringCase() throws Exception {
		createGoal("PRODUCT_CREW", "Product Crew");

		mockMvc.perform(post("/api/goals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "ANOTHER_PRODUCT_CREW",
								  "name": "product crew",
								  "description": "Duplicate name"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Goal already exists: product crew"));
	}

	@Test
	void getGoalReturnsExistingGoal() throws Exception {
		String location = createGoal("AI_STUDY_GROUP", "AI Study Group");

		mockMvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("AI_STUDY_GROUP"))
				.andExpect(jsonPath("$.name").value("AI Study Group"));
	}

	@Test
	void getGoalReturnsNotFoundForMissingGoal() throws Exception {
		mockMvc.perform(get("/api/goals/00000000-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.message")
						.value("Goal not found: 00000000-0000-0000-0000-000000000000"));
	}

	@Test
	void getAllGoalsReturnsSeededGoals() throws Exception {
		mockMvc.perform(get("/api/goals"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("TEAM_SEARCH")))
				.andExpect(content().string(containsString("HACKATHON_TEAM")));
	}

	@Test
	void updateGoalChangesProvidedFields() throws Exception {
		String location = createGoal("OLD_GOAL_CODE", "Old Goal Name");

		mockMvc.perform(patch(location)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "UPDATED_GOAL_CODE",
								  "name": "  Updated Goal Name  ",
								  "description": "Updated goal description"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("UPDATED_GOAL_CODE"))
				.andExpect(jsonPath("$.name").value("Updated Goal Name"))
				.andExpect(jsonPath("$.description").value("Updated goal description"));
	}

	@Test
	void updateGoalRejectsBlankName() throws Exception {
		String location = createGoal("BLANK_NAME_GOAL", "Blank Name Goal");

		mockMvc.perform(patch(location)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "   "
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("name")));
	}

	@Test
	void deleteGoalRemovesIt() throws Exception {
		String location = createGoal("GOAL_TO_DELETE", "Goal To Delete");

		mockMvc.perform(delete(location))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(location))
				.andExpect(status().isNotFound());
	}

	private String createGoal(String code, String name) throws Exception {
		return mockMvc.perform(post("/api/goals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "code": "%s",
								  "name": "%s",
								  "description": "Created for goal tests"
								}
								""".formatted(code, name)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");
	}
}
