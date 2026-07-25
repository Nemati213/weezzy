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
class ProfileSkillControllerTests {

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
	void addSkillToProfileReturnsLinkedSkill() throws Exception {
		String profileLocation = createProfile("Skill Link Profile");
		String skillLocation = createSkill("Profile Skill Link Java");

		mockMvc.perform(post(profileLocation + "/skills/" + idFromLocation(skillLocation)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern(profileLocation + "/skills/.+")))
				.andExpect(jsonPath("$.name").value("Profile Skill Link Java"));
	}

	@Test
	void getProfileSkillsReturnsLinkedSkills() throws Exception {
		String profileLocation = createProfile("Skill List Profile");
		String skillLocation = createSkill("Profile Skill List Spring");

		mockMvc.perform(post(profileLocation + "/skills/" + idFromLocation(skillLocation)))
				.andExpect(status().isCreated());

		mockMvc.perform(get(profileLocation + "/skills"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Profile Skill List Spring")));
	}

	@Test
	void addSkillToProfileRejectsDuplicateLink() throws Exception {
		String profileLocation = createProfile("Skill Duplicate Profile");
		String skillLocation = createSkill("Profile Skill Duplicate Docker");
		String linkUrl = profileLocation + "/skills/" + idFromLocation(skillLocation);

		mockMvc.perform(post(linkUrl))
				.andExpect(status().isCreated());

		mockMvc.perform(post(linkUrl))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.message").value(containsString("Profile already has skill")))
				.andExpect(jsonPath("$.path").value(linkUrl));
	}

	@Test
	void removeSkillFromProfileDeletesLink() throws Exception {
		String profileLocation = createProfile("Skill Delete Profile");
		String skillLocation = createSkill("Profile Skill Delete PostgreSQL");
		String linkUrl = profileLocation + "/skills/" + idFromLocation(skillLocation);

		mockMvc.perform(post(linkUrl))
				.andExpect(status().isCreated());

		mockMvc.perform(delete(linkUrl))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(profileLocation + "/skills"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Profile Skill Delete PostgreSQL"))));
	}

	@Test
	void addSkillToMissingProfileReturnsNotFound() throws Exception {
		String skillLocation = createSkill("Profile Skill Missing Profile");

		mockMvc.perform(post("/api/profiles/00000000-0000-0000-0000-000000000000/skills/"
						+ idFromLocation(skillLocation)))
				.andExpect(status().isNotFound());
	}

	@Test
	void addMissingSkillToProfileReturnsNotFound() throws Exception {
		String profileLocation = createProfile("Missing Skill Profile");

		mockMvc.perform(post(profileLocation + "/skills/00000000-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound());
	}

	@Test
	void removeMissingProfileSkillLinkReturnsNotFound() throws Exception {
		String profileLocation = createProfile("Missing Link Profile");
		String skillLocation = createSkill("Profile Skill Missing Link");

		mockMvc.perform(delete(profileLocation + "/skills/" + idFromLocation(skillLocation)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value(containsString("Profile skill link not found")));
	}

	private String createProfile(String displayName) throws Exception {
		return mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "%s",
								  "bio": "Created for profile-skill tests",
								  "telegram": "@profile_skill_test",
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
								  "description": "Created for profile-skill tests"
								}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");
	}

	private String idFromLocation(String location) {
		return location.substring(location.lastIndexOf('/') + 1);
	}
}
