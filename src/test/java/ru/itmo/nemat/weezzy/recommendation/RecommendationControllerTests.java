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
	void recommendationsReturnProfilesSortedBySharedSkills() throws Exception {
		String source = createProfile("Recommendation Source");
		String alice = createProfile("Recommendation Alice");
		String timur = createProfile("Recommendation Timur");
		String bob = createProfile("Recommendation Bob");

		String java = createSkill("Recommendation Java");
		String spring = createSkill("Recommendation Spring");
		String postgresSkill = createSkill("Recommendation PostgreSQL");
		String python = createSkill("Recommendation Python");

		addSkill(source, java);
		addSkill(source, spring);
		addSkill(source, postgresSkill);

		addSkill(alice, java);
		addSkill(alice, spring);
		addSkill(alice, postgresSkill);

		addSkill(timur, java);
		addSkill(timur, spring);

		addSkill(bob, python);

		mockMvc.perform(get(source + "/recommendations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].profile.displayName").value("Recommendation Alice"))
				.andExpect(jsonPath("$[0].score").value(3))
				.andExpect(jsonPath("$[0].matchedSkills[0]").value("Recommendation Java"))
				.andExpect(jsonPath("$[0].matchedSkills[1]").value("Recommendation PostgreSQL"))
				.andExpect(jsonPath("$[0].matchedSkills[2]").value("Recommendation Spring"))
				.andExpect(jsonPath("$[1].profile.displayName").value("Recommendation Timur"))
				.andExpect(jsonPath("$[1].score").value(2))
				.andExpect(content().string(not(containsString("Recommendation Bob"))))
				.andExpect(content().string(not(containsString("Recommendation Source"))));
	}

	@Test
	void recommendationsReturnEmptyListWhenProfileHasNoSkills() throws Exception {
		String source = createProfile("Recommendation No Skills Source");
		String candidate = createProfile("Recommendation No Skills Candidate");
		String skill = createSkill("Recommendation No Skills Java");
		addSkill(candidate, skill);

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

	private void addSkill(String profileLocation, String skillLocation) throws Exception {
		mockMvc.perform(post(profileLocation + "/skills/" + idFromLocation(skillLocation)))
				.andExpect(status().isCreated());
	}

	private String idFromLocation(String location) {
		return location.substring(location.lastIndexOf('/') + 1);
	}
}
