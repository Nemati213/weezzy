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
class ProfileInterestControllerTests {

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
	void currentUserCanAddAndReadOwnInterest() throws Exception {
		TestProfile profile = createProfile("Interest Link Profile");
		String interestId = idFromLocation(createInterest("Profile Interest Startups"));

		mockMvc.perform(profile.owner().authorize(post("/api/profiles/me/interests/" + interestId)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/profiles/me/interests/" + interestId))
				.andExpect(jsonPath("$.name").value("Profile Interest Startups"));

		mockMvc.perform(profile.owner().authorize(get("/api/profiles/me/interests")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Profile Interest Startups")));
	}

	@Test
	void addInterestRejectsDuplicateLink() throws Exception {
		TestProfile profile = createProfile("Interest Duplicate Profile");
		String interestId = idFromLocation(createInterest("Profile Interest Duplicate"));
		String linkUrl = "/api/profiles/me/interests/" + interestId;

		mockMvc.perform(profile.owner().authorize(post(linkUrl)))
				.andExpect(status().isCreated());
		mockMvc.perform(profile.owner().authorize(post(linkUrl)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString("Profile already has interest")));
	}

	@Test
	void currentUserCanRemoveOwnInterest() throws Exception {
		TestProfile profile = createProfile("Interest Delete Profile");
		String interestId = idFromLocation(createInterest("Profile Interest Delete"));
		String linkUrl = "/api/profiles/me/interests/" + interestId;

		mockMvc.perform(profile.owner().authorize(post(linkUrl)))
				.andExpect(status().isCreated());
		mockMvc.perform(profile.owner().authorize(delete(linkUrl)))
				.andExpect(status().isNoContent());
		mockMvc.perform(profile.owner().authorize(get("/api/profiles/me/interests")))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Profile Interest Delete"))));
	}

	@Test
	void interestEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/profiles/me/interests"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void interestEndpointReturnsNotFoundWhenCurrentUserHasNoProfile() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(get("/api/profiles/me/interests")))
				.andExpect(status().isNotFound());
	}

	@Test
	void addMissingInterestReturnsNotFound() throws Exception {
		TestProfile profile = createProfile("Missing Interest Profile");

		mockMvc.perform(profile.owner().authorize(
						post("/api/profiles/me/interests/00000000-0000-0000-0000-000000000000")))
				.andExpect(status().isNotFound());
	}

	private TestProfile createProfile(String displayName) throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile(displayName);
	}

	private String createInterest(String name) throws Exception {
		AuthenticatedTestUser admin = AuthenticatedTestUser.registerAdmin(
				mockMvc,
				objectMapper,
				userRepository,
				jwtService
		);

		return mockMvc.perform(admin.authorize(post("/api/interests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "Created for profile-interest tests"
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
