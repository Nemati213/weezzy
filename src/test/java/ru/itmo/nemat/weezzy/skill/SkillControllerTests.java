package ru.itmo.nemat.weezzy.skill;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SkillControllerTests {

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
	void createSkillReturnsCreatedSkill() throws Exception {
		mockMvc.perform(post("/api/skills")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Java",
								  "description": "Backend programming language"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern("/api/skills/.+")))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Java"))
				.andExpect(jsonPath("$.description").value("Backend programming language"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	void createSkillTrimsName() throws Exception {
		mockMvc.perform(post("/api/skills")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "  Spring Boot  ",
								  "description": "Java backend framework"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Spring Boot"));
	}

	@Test
	void createSkillRejectsBlankName() throws Exception {
		mockMvc.perform(post("/api/skills")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "",
								  "description": "Nope"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value(containsString("name")))
				.andExpect(jsonPath("$.path").value("/api/skills"));
	}

	@Test
	void createSkillRejectsDuplicateNameIgnoringCase() throws Exception {
		mockMvc.perform(post("/api/skills")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "PostgreSQL",
								  "description": "Database"
								}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/skills")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "postgresql",
								  "description": "Same database"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.message").value("Skill already exists: postgresql"))
				.andExpect(jsonPath("$.path").value("/api/skills"));
	}

	@Test
	void getSkillReturnsExistingSkill() throws Exception {
		String location = mockMvc.perform(post("/api/skills")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Machine Learning",
								  "description": "Recommendation systems and models"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");

		mockMvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Machine Learning"))
				.andExpect(jsonPath("$.description").value("Recommendation systems and models"));
	}

	@Test
	void getSkillReturnsNotFoundForMissingSkill() throws Exception {
		mockMvc.perform(get("/api/skills/00000000-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("Skill not found: 00000000-0000-0000-0000-000000000000"))
				.andExpect(jsonPath("$.path").value("/api/skills/00000000-0000-0000-0000-000000000000"));
	}

	@Test
	void getAllSkillsReturnsCreatedSkills() throws Exception {
		mockMvc.perform(post("/api/skills")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Docker",
								  "description": "Containers"
								}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/skills"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Docker")))
				.andExpect(content().string(containsString("Containers")));
	}
}
