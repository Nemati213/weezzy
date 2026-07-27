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
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
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

	@Autowired
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void currentUserCanAddAndReadOwnSkill() throws Exception {
		TestProfile profile = createProfile("Skill Link Profile");
		String skillId = idFromLocation(createSkill("Profile Skill Link Java"));

		mockMvc.perform(profile.owner().authorize(post("/api/profiles/me/skills/" + skillId)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/profiles/me/skills/" + skillId))
				.andExpect(jsonPath("$.name").value("Profile Skill Link Java"));

		mockMvc.perform(profile.owner().authorize(get("/api/profiles/me/skills")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Profile Skill Link Java")));
	}

	@Test
	void currentUserCannotModifyAnotherUsersSkillsByPuttingProfileIdInUrl() throws Exception {
		TestProfile profile = createProfile("Skill Owner");
		String skillId = idFromLocation(createSkill("Profile Skill Protected"));

		mockMvc.perform(profile.owner().authorize(post(profile.location() + "/skills/" + skillId)))
				.andExpect(status().isNotFound());
	}

	@Test
	void addSkillRejectsDuplicateLink() throws Exception {
		TestProfile profile = createProfile("Skill Duplicate Profile");
		String skillId = idFromLocation(createSkill("Profile Skill Duplicate Docker"));
		String linkUrl = "/api/profiles/me/skills/" + skillId;

		mockMvc.perform(profile.owner().authorize(post(linkUrl)))
				.andExpect(status().isCreated());

		mockMvc.perform(profile.owner().authorize(post(linkUrl)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString("Profile already has skill")));
	}

	@Test
	void currentUserCanRemoveOwnSkill() throws Exception {
		TestProfile profile = createProfile("Skill Delete Profile");
		String skillId = idFromLocation(createSkill("Profile Skill Delete PostgreSQL"));
		String linkUrl = "/api/profiles/me/skills/" + skillId;

		mockMvc.perform(profile.owner().authorize(post(linkUrl)))
				.andExpect(status().isCreated());
		mockMvc.perform(profile.owner().authorize(delete(linkUrl)))
				.andExpect(status().isNoContent());
		mockMvc.perform(profile.owner().authorize(get("/api/profiles/me/skills")))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Profile Skill Delete PostgreSQL"))));
	}

	@Test
	void skillEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/profiles/me/skills"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void skillEndpointReturnsNotFoundWhenCurrentUserHasNoProfile() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(get("/api/profiles/me/skills")))
				.andExpect(status().isNotFound());
	}

	@Test
	void addMissingSkillReturnsNotFound() throws Exception {
		TestProfile profile = createProfile("Missing Skill Profile");

		mockMvc.perform(profile.owner().authorize(
						post("/api/profiles/me/skills/00000000-0000-0000-0000-000000000000")))
				.andExpect(status().isNotFound());
	}

	private TestProfile createProfile(String displayName) throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile(displayName);
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
