package ru.itmo.nemat.weezzy.interest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.security.JwtService;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.user.UserRepository;
import tools.jackson.databind.ObjectMapper;

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
class InterestControllerTests {

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
	void createInterestReturnsCreatedInterest() throws Exception {
		mockMvc.perform(authorizeAdmin(post("/api/interests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Startups",
								  "description": "Building products and companies"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern("/api/interests/.+")))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.name").value("Startups"))
				.andExpect(jsonPath("$.description").value("Building products and companies"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	void createInterestTrimsName() throws Exception {
		mockMvc.perform(authorizeAdmin(post("/api/interests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "  Hackathons  ",
								  "description": "Team competitions"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Hackathons"));
	}

	@Test
	void createInterestRejectsBlankName() throws Exception {
		mockMvc.perform(authorizeAdmin(post("/api/interests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "",
								  "description": "Invalid interest"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value(containsString("name")))
				.andExpect(jsonPath("$.path").value("/api/interests"));
	}

	@Test
	void createInterestRejectsDuplicateNameIgnoringCase() throws Exception {
		createInterest("Open Source");

		mockMvc.perform(authorizeAdmin(post("/api/interests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "open source",
								  "description": "Duplicate"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value("Interest already exists: open source"));
	}

	@Test
	void getInterestReturnsExistingInterest() throws Exception {
		String location = createInterest("Artificial Intelligence");

		mockMvc.perform(authorize(get(location)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Artificial Intelligence"))
				.andExpect(jsonPath("$.description").value("Created for interest tests"));
	}

	@Test
	void getInterestReturnsNotFoundForMissingInterest() throws Exception {
		mockMvc.perform(authorize(get("/api/interests/00000000-0000-0000-0000-000000000000")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.message")
						.value("Interest not found: 00000000-0000-0000-0000-000000000000"));
	}

	@Test
	void getAllInterestsReturnsCreatedInterests() throws Exception {
		createInterest("Product Design");

		mockMvc.perform(authorize(get("/api/interests")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Product Design")));
	}

	@Test
	void updateInterestChangesProvidedFields() throws Exception {
		String location = createInterest("Robotics");

		mockMvc.perform(authorizeAdmin(patch(location))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "  Autonomous Robotics  ",
								  "description": "Robots and control systems"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Autonomous Robotics"))
				.andExpect(jsonPath("$.description").value("Robots and control systems"));
	}

	@Test
	void updateInterestRejectsBlankName() throws Exception {
		String location = createInterest("Computer Vision");

		mockMvc.perform(authorizeAdmin(patch(location))
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
	void deleteInterestRemovesIt() throws Exception {
		String location = createInterest("Interest To Delete");

		mockMvc.perform(authorizeAdmin(delete(location)))
				.andExpect(status().isNoContent());

		mockMvc.perform(authorize(get(location)))
				.andExpect(status().isNotFound());
	}

	@Test
	void interestCatalogRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/interests"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void regularUserCannotCreateInterest() throws Exception {
		mockMvc.perform(authorize(post("/api/interests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Forbidden Interest"
								}
								"""))
				.andExpect(status().isForbidden());
	}

	private String createInterest(String name) throws Exception {
		return mockMvc.perform(authorizeAdmin(post("/api/interests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "Created for interest tests"
								}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");
	}

	private MockHttpServletRequestBuilder authorize(MockHttpServletRequestBuilder request) throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper).authorize(request);
	}

	private MockHttpServletRequestBuilder authorizeAdmin(
			MockHttpServletRequestBuilder request
	) throws Exception {
		return AuthenticatedTestUser.registerAdmin(
				mockMvc,
				objectMapper,
				userRepository,
				jwtService
		).authorize(request);
	}
}
