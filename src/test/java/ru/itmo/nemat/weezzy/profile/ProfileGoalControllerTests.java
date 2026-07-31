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
import ru.itmo.nemat.weezzy.security.JwtService;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.user.UserRepository;
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

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void currentUserCanAddAndReadOwnGoal() throws Exception {
		TestProfile profile = createProfile("Goal Link Profile");
		String goalId = idFromLocation(createGoal("PROFILE_GOAL_TEAM", "Profile Goal Team"));

		mockMvc.perform(profile.owner().authorize(post("/api/profiles/me/goals/" + goalId)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/profiles/me/goals/" + goalId))
				.andExpect(jsonPath("$.code").value("PROFILE_GOAL_TEAM"));

		mockMvc.perform(profile.owner().authorize(get("/api/profiles/me/goals")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Profile Goal Team")));
	}

	@Test
	void addGoalRejectsDuplicateLink() throws Exception {
		TestProfile profile = createProfile("Goal Duplicate Profile");
		String goalId = idFromLocation(createGoal("PROFILE_GOAL_DUPLICATE", "Profile Goal Duplicate"));
		String linkUrl = "/api/profiles/me/goals/" + goalId;

		mockMvc.perform(profile.owner().authorize(post(linkUrl)))
				.andExpect(status().isCreated());
		mockMvc.perform(profile.owner().authorize(post(linkUrl)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString("Profile already has goal")));
	}

	@Test
	void currentUserCanRemoveOwnGoal() throws Exception {
		TestProfile profile = createProfile("Goal Delete Profile");
		String goalId = idFromLocation(createGoal("PROFILE_GOAL_DELETE", "Profile Goal Delete"));
		String linkUrl = "/api/profiles/me/goals/" + goalId;

		mockMvc.perform(profile.owner().authorize(post(linkUrl)))
				.andExpect(status().isCreated());
		mockMvc.perform(profile.owner().authorize(delete(linkUrl)))
				.andExpect(status().isNoContent());
		mockMvc.perform(profile.owner().authorize(get("/api/profiles/me/goals")))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("PROFILE_GOAL_DELETE"))));
	}

	@Test
	void goalEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/profiles/me/goals"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void goalEndpointReturnsNotFoundWhenCurrentUserHasNoProfile() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(get("/api/profiles/me/goals")))
				.andExpect(status().isNotFound());
	}

	@Test
	void addMissingGoalReturnsNotFound() throws Exception {
		TestProfile profile = createProfile("Missing Goal Profile");

		mockMvc.perform(profile.owner().authorize(
						post("/api/profiles/me/goals/00000000-0000-0000-0000-000000000000")))
				.andExpect(status().isNotFound());
	}

	private TestProfile createProfile(String displayName) throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile(displayName);
	}

	private String createGoal(String code, String name) throws Exception {
		AuthenticatedTestUser admin = AuthenticatedTestUser.registerAdmin(
				mockMvc,
				objectMapper,
				userRepository,
				jwtService
		);

		return mockMvc.perform(admin.authorize(post("/api/goals"))
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

	private String idFromLocation(String location) {
		return location.substring(location.lastIndexOf('/') + 1);
	}
}
