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
import static org.hamcrest.Matchers.matchesPattern;
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
class ProfileControllerTests {

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
	void authenticatedUserCreatesOwnProfile() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		createProfile(user, "Nemat", 2)
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern("/api/profiles/.+")))
				.andExpect(jsonPath("$.displayName").value("Nemat"))
				.andExpect(jsonPath("$.course").value(2))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.userId").value(user.userId()));
	}

	@Test
	void createProfileRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validProfileJson("Anonymous", 2)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void userCannotCreateSecondProfile() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		createProfile(user, "First Profile", 2).andExpect(status().isCreated());

		createProfile(user, "Second Profile", 2)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Profile already exists for user: " + user.userId()));
	}

	@Test
	void createProfileValidatesRequest() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		createProfile(user, "", 7)
				.andExpect(status().isBadRequest());
	}

	@Test
	void authenticatedUserCanReadProfileById() throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile("Profile To Fetch");
		AuthenticatedTestUser viewer = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(viewer.authorize(get(profile.location())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Profile To Fetch"))
				.andExpect(jsonPath("$.userId").value(profile.owner().userId()));
	}

	@Test
	void currentUserReadsOwnProfileWithoutProfileId() throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile("My Profile");

		mockMvc.perform(profile.owner().authorize(get("/api/profiles/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(profile.id()))
				.andExpect(jsonPath("$.displayName").value("My Profile"));
	}

	@Test
	void getAllProfilesReturnsCreatedProfiles() throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile("Profile In List");

		mockMvc.perform(profile.owner().authorize(get("/api/profiles")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Profile In List")));
	}

	@Test
	void currentUserUpdatesOwnProfileWithoutProfileId() throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile("Before Update");

		mockMvc.perform(profile.owner().authorize(patch("/api/profiles/me"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bio": "New bio",
								  "course": 4,
								  "status": "ACTIVE"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Before Update"))
				.andExpect(jsonPath("$.bio").value("New bio"))
				.andExpect(jsonPath("$.course").value(4))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void updateProfileValidatesRequest() throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile("Valid Profile");

		mockMvc.perform(profile.owner().authorize(patch("/api/profiles/me"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "",
								  "course": 0
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void currentProfileEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/profiles/me"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/api/profiles/me")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bio": "No token"
								}
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void profileReadEndpointsRequireAuthentication() throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile("Protected Profile");

		mockMvc.perform(get(profile.location()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/profiles"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void currentProfileReturnsNotFoundWhenUserHasNoProfile() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(get("/api/profiles/me")))
				.andExpect(status().isNotFound());
	}

	private org.springframework.test.web.servlet.ResultActions createProfile(
			AuthenticatedTestUser user,
			String displayName,
			int course
	) throws Exception {
		return mockMvc.perform(user.authorize(post("/api/profiles"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validProfileJson(displayName, course)));
	}

	private String validProfileJson(String displayName, int course) {
		return """
				{
				  "displayName": "%s",
				  "bio": "Backend developer at ITMO",
				  "telegram": "@profile_test",
				  "faculty": "FICT",
				  "studyProgram": "Software Engineering",
				  "course": %d
				}
				""".formatted(displayName, course);
	}
}
