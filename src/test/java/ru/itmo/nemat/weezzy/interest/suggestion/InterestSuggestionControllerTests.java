package ru.itmo.nemat.weezzy.interest.suggestion;

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
import ru.itmo.nemat.weezzy.user.UserRepository;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
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
class InterestSuggestionControllerTests {

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
	void authenticatedUserCreatesInterestSuggestion() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(post("/api/interest-suggestions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Board Games",
								  "description": "Meet people for strategy games"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern("/api/interest-suggestions/.+")))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.suggestedByUserId").value(user.userId()))
				.andExpect(jsonPath("$.name").value("Board Games"))
				.andExpect(jsonPath("$.description").value("Meet people for strategy games"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.reviewedAt").doesNotExist())
				.andExpect(jsonPath("$.reviewedByUserId").doesNotExist());
	}

	@Test
	void createInterestSuggestionTrimsName() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(post("/api/interest-suggestions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "  Public Speaking  ",
								  "description": "Talks and presentations"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Public Speaking"));
	}

	@Test
	void currentUserReadsOnlyOwnInterestSuggestions() throws Exception {
		AuthenticatedTestUser firstUser = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser secondUser = AuthenticatedTestUser.register(mockMvc, objectMapper);

		createSuggestion(firstUser, "Product Analytics Suggestions");
		createSuggestion(secondUser, "Creative Coding Suggestions");

		mockMvc.perform(firstUser.authorize(get("/api/interest-suggestions/me")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Product Analytics Suggestions")))
				.andExpect(content().string(not(containsString("Creative Coding Suggestions"))));
	}

	@Test
	void interestSuggestionsRequireAuthentication() throws Exception {
		mockMvc.perform(post("/api/interest-suggestions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "No Token"
								}
								"""))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/interest-suggestions/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void createInterestSuggestionRejectsBlankName() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(post("/api/interest-suggestions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "   ",
								  "description": "Invalid suggestion"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("name")));
	}

	@Test
	void createInterestSuggestionRejectsExistingCatalogInterest() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		createInterest("Existing Interest From Catalog");

		mockMvc.perform(user.authorize(post("/api/interest-suggestions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "existing interest from catalog",
								  "description": "Already exists"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Interest already exists: existing interest from catalog"));
	}

	@Test
	void createInterestSuggestionRejectsDuplicatePendingSuggestionForSameUser() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		createSuggestion(user, "Duplicate Pending Interest");

		mockMvc.perform(user.authorize(post("/api/interest-suggestions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "duplicate pending interest",
								  "description": "Same suggestion"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message")
						.value("Interest suggestion already exists: duplicate pending interest"));
	}

	@Test
	void interestModerationRequiresAdminRole() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		String suggestionId = createSuggestion(user, "Protected Interest Suggestion");
		String queueUrl = "/api/admin/interest-suggestions";
		String approveUrl = "/api/admin/interest-suggestions/" + suggestionId + "/approve";

		mockMvc.perform(get(queueUrl))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(patch(approveUrl))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(user.authorize(get(queueUrl)))
				.andExpect(status().isForbidden());

		mockMvc.perform(user.authorize(patch(approveUrl)))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminListsInterestSuggestionsByStatusWithPagination() throws Exception {
		AuthenticatedTestUser submitter = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser admin = registerAdmin();
		String firstPending = "Admin Queue Interest Pending One";
		String secondPending = "Admin Queue Interest Pending Two";
		String approvedName = "Admin Queue Interest Approved";

		createSuggestion(submitter, firstPending);
		createSuggestion(submitter, secondPending);
		String approvedId = createSuggestion(submitter, approvedName);

		mockMvc.perform(admin.authorize(patch(
						"/api/admin/interest-suggestions/" + approvedId + "/approve")))
				.andExpect(status().isNoContent());

		mockMvc.perform(admin.authorize(get("/api/admin/interest-suggestions")
						.param("size", "100")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content[*].status").value(
						org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PENDING"))))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(100))
				.andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)))
				.andExpect(content().string(containsString(firstPending)))
				.andExpect(content().string(containsString(secondPending)))
				.andExpect(content().string(not(containsString(approvedName))));

		mockMvc.perform(admin.authorize(get("/api/admin/interest-suggestions")
						.param("status", "APPROVED")
						.param("size", "100")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].status").value(
						org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("APPROVED"))))
				.andExpect(content().string(containsString(approvedName)));

		mockMvc.perform(admin.authorize(get("/api/admin/interest-suggestions")
						.param("status", "PENDING")
						.param("page", "0")
						.param("size", "1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.totalPages").value(greaterThanOrEqualTo(2)))
				.andExpect(jsonPath("$.hasNext").value(true))
				.andExpect(jsonPath("$.hasPrevious").value(false));
	}

	@Test
	void adminApprovesInterestSuggestionAndCreatesCatalogInterest() throws Exception {
		AuthenticatedTestUser submitter = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser admin = registerAdmin();
		String suggestionName = "Approved Interest Suggestion";
		String suggestionId = createSuggestion(submitter, suggestionName);
		String moderationUrl = "/api/admin/interest-suggestions/" + suggestionId;

		mockMvc.perform(admin.authorize(patch(moderationUrl + "/approve")))
				.andExpect(status().isNoContent());

		mockMvc.perform(submitter.authorize(get("/api/interest-suggestions/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].suggestedByUserId").value(submitter.userId()))
				.andExpect(jsonPath("$[0].status").value("APPROVED"))
				.andExpect(jsonPath("$[0].reviewedAt").isNotEmpty())
				.andExpect(jsonPath("$[0].reviewedByUserId").value(admin.userId()));

		mockMvc.perform(submitter.authorize(get("/api/interests")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(suggestionName)));

		mockMvc.perform(admin.authorize(patch(moderationUrl + "/reject")))
				.andExpect(status().isConflict());
	}

	@Test
	void adminRejectsInterestSuggestionWithoutCreatingCatalogInterest() throws Exception {
		AuthenticatedTestUser submitter = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser admin = registerAdmin();
		String suggestionName = "Rejected Interest Suggestion";
		String suggestionId = createSuggestion(submitter, suggestionName);

		mockMvc.perform(admin.authorize(patch(
						"/api/admin/interest-suggestions/" + suggestionId + "/reject")))
				.andExpect(status().isNoContent());

		mockMvc.perform(submitter.authorize(get("/api/interest-suggestions/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("REJECTED"))
				.andExpect(jsonPath("$[0].reviewedByUserId").value(admin.userId()));

		mockMvc.perform(submitter.authorize(get("/api/interests")))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString(suggestionName))));
	}

	@Test
	void adminModerationReturnsNotFoundForMissingInterestSuggestion() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();

		mockMvc.perform(admin.authorize(patch(
						"/api/admin/interest-suggestions/00000000-0000-0000-0000-000000000000/approve")))
				.andExpect(status().isNotFound());
	}

	private String createSuggestion(AuthenticatedTestUser user, String name) throws Exception {
		String response = mockMvc.perform(user.authorize(post("/api/interest-suggestions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "Created for interest suggestion tests"
								}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		return objectMapper.readTree(response).path("id").asText();
	}

	private void createInterest(String name) throws Exception {
		AuthenticatedTestUser admin = registerAdmin();

		mockMvc.perform(admin.authorize(post("/api/interests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "Created for interest suggestion tests"
								}
								""".formatted(name)))
				.andExpect(status().isCreated());
	}

	private AuthenticatedTestUser registerAdmin() throws Exception {
		return AuthenticatedTestUser.registerAdmin(
				mockMvc,
				objectMapper,
				userRepository,
				jwtService
		);
	}
}
