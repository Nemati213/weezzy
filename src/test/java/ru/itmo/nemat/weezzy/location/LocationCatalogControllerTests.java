package ru.itmo.nemat.weezzy.location;

import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class LocationCatalogControllerTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

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

	private AuthenticatedTestUser user;
	private AuthenticatedTestUser admin;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void registerUsers() throws Exception {
		user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		admin = AuthenticatedTestUser.registerAdmin(
				mockMvc,
				objectMapper,
				userRepository,
				jwtService
		);
	}

	@Test
	void adminCanCreateAndReadUniversity() throws Exception {
		String name = unique("ITMO University");

		String id = createUniversity(name, "  Saint Petersburg  ");

		mockMvc.perform(user.authorize(get("/api/universities/{id}", id)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.name").value(name))
				.andExpect(jsonPath("$.city").value("Saint Petersburg"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty());
	}

	@Test
	void universityCatalogRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/universities"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/locations"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void regularUserCannotCreateCatalogEntries() throws Exception {
		mockMvc.perform(user.authorize(post("/api/universities"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Forbidden University",
								  "city": "Saint Petersburg"
								}
								"""))
				.andExpect(status().isForbidden());

		mockMvc.perform(user.authorize(post("/api/locations"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void createUniversityValidatesRequest() throws Exception {
		mockMvc.perform(admin.authorize(post("/api/universities"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": " ",
								  "city": "Saint Petersburg"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("name")));
	}

	@Test
	void duplicateUniversityReturnsConflictIgnoringCase() throws Exception {
		String name = unique("Duplicate University");
		createUniversity(name, "Moscow");

		mockMvc.perform(admin.authorize(post("/api/universities"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "city": "moscow"
								}
								""".formatted(name.toLowerCase())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", containsString("University already exists")));
	}

	@Test
	void adminCanCreateLocationAndUsersCanFilterCatalog() throws Exception {
		String universityId = createUniversity(unique("Filter University"), "Kronstadt");
		String locationName = unique("Main Canteen");
		String locationId = createLocation(
				universityId,
				"DINING_ROOM",
				locationName,
				"Kronverksky Prospekt 49"
		);
		createLocation(
				universityId,
				"LIBRARY",
				unique("Library"),
				"Kronverksky Prospekt 49"
		);

		mockMvc.perform(user.authorize(get("/api/locations"))
						.param("universityId", universityId)
						.param("type", "DINING_ROOM"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(locationId))
				.andExpect(jsonPath("$.content[0].name").value(locationName))
				.andExpect(jsonPath("$.content[0].type").value("DINING_ROOM"))
				.andExpect(jsonPath("$.content[0].active").value(true))
				.andExpect(jsonPath("$.content[0].university.id").value(universityId));
	}

	@Test
	void createLocationReturnsNotFoundForMissingUniversity() throws Exception {
		UUID missingId = UUID.randomUUID();

		mockMvc.perform(admin.authorize(post("/api/locations"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(locationJson(
								missingId.toString(),
								"DINING_ROOM",
								"Missing University Canteen",
								"Unknown address"
						)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("University not found: " + missingId));
	}

	@Test
	void duplicateLocationReturnsConflictIgnoringCase() throws Exception {
		String universityId = createUniversity(unique("Duplicate Location University"), "Perm");
		String name = unique("Canteen");
		createLocation(universityId, "DINING_ROOM", name, "Lenina 1");

		mockMvc.perform(admin.authorize(post("/api/locations"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(locationJson(
								universityId,
								"DINING_ROOM",
								name.toLowerCase(),
								"lenina 1"
						)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", containsString("Location already exists")));
	}

	private String createUniversity(String name, String city) throws Exception {
		String response = mockMvc.perform(admin.authorize(post("/api/universities"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "city": "%s"
								}
								""".formatted(name, city)))
				.andExpect(status().isCreated())
				.andExpect(header().string(
						"Location",
						matchesPattern("/api/universities/.+")
				))
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response).path("id").asText();
	}

	private String createLocation(
			String universityId,
			String type,
			String name,
			String address
	) throws Exception {
		String response = mockMvc.perform(admin.authorize(post("/api/locations"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(locationJson(universityId, type, name, address)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern("/api/locations/.+")))
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response).path("id").asText();
	}

	private String locationJson(
			String universityId,
			String type,
			String name,
			String address
	) throws Exception {
		JsonNode payload = objectMapper.createObjectNode()
				.put("universityId", universityId)
				.put("type", type)
				.put("name", name)
				.put("address", address)
				.put("description", "Lunch location");
		return objectMapper.writeValueAsString(payload);
	}

	private String unique(String prefix) {
		return prefix + " " + UUID.randomUUID();
	}
}
