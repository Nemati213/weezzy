package ru.itmo.nemat.weezzy.skill.suggestion;

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
class SkillSuggestionControllerTests {

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
	void authenticatedUserCreatesSkillSuggestion() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(post("/api/skill-suggestions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Rust",
								  "description": "Systems programming"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern("/api/skill-suggestions/.+")))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.suggestedByUserId").value(user.userId()))
				.andExpect(jsonPath("$.name").value("Rust"))
				.andExpect(jsonPath("$.description").value("Systems programming"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.reviewedAt").doesNotExist())
				.andExpect(jsonPath("$.reviewedByUserId").doesNotExist());
	}

	@Test
	void createSkillSuggestionTrimsName() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(post("/api/skill-suggestions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "  Kotlin  ",
								  "description": "Backend and Android"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Kotlin"));
	}

	@Test
	void currentUserReadsOnlyOwnSkillSuggestions() throws Exception {
		AuthenticatedTestUser firstUser = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser secondUser = AuthenticatedTestUser.register(mockMvc, objectMapper);

		createSuggestion(firstUser, "SwiftUI Suggestions");
		createSuggestion(secondUser, "Flutter Suggestions");

		mockMvc.perform(firstUser.authorize(get("/api/skill-suggestions/me")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("SwiftUI Suggestions")))
				.andExpect(content().string(not(containsString("Flutter Suggestions"))));
	}

	@Test
	void skillSuggestionsRequireAuthentication() throws Exception {
		mockMvc.perform(post("/api/skill-suggestions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "No Token"
								}
								"""))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/skill-suggestions/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void createSkillSuggestionRejectsBlankName() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(post("/api/skill-suggestions"))
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
	void createSkillSuggestionRejectsExistingCatalogSkill() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		createSkill("Existing Skill From Catalog");

		mockMvc.perform(user.authorize(post("/api/skill-suggestions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "existing skill from catalog",
								  "description": "Already exists"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Skill already exists: existing skill from catalog"));
	}

	@Test
	void createSkillSuggestionRejectsDuplicatePendingSuggestionForSameUser() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		createSuggestion(user, "Duplicate Pending Suggestion");

		mockMvc.perform(user.authorize(post("/api/skill-suggestions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "duplicate pending suggestion",
								  "description": "Same suggestion"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message")
						.value("Skill suggestion already exists: duplicate pending suggestion"));
	}

	@Test
	void skillModerationRequiresAdminRole() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		String suggestionId = createSuggestion(user, "Protected Skill Suggestion");
		String queueUrl = "/api/admin/skill-suggestions";
		String approveUrl = "/api/admin/skill-suggestions/" + suggestionId + "/approve";

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
	void adminListsSkillSuggestionsByStatusWithPagination() throws Exception {
		AuthenticatedTestUser submitter = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser admin = registerAdmin();
		String firstPending = "Admin Queue Skill Pending One";
		String secondPending = "Admin Queue Skill Pending Two";
		String approvedName = "Admin Queue Skill Approved";

		createSuggestion(submitter, firstPending);
		createSuggestion(submitter, secondPending);
		String approvedId = createSuggestion(submitter, approvedName);

		mockMvc.perform(admin.authorize(patch(
						"/api/admin/skill-suggestions/" + approvedId + "/approve")))
				.andExpect(status().isNoContent());

		mockMvc.perform(admin.authorize(get("/api/admin/skill-suggestions")
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

		mockMvc.perform(admin.authorize(get("/api/admin/skill-suggestions")
						.param("status", "APPROVED")
						.param("size", "100")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].status").value(
						org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("APPROVED"))))
				.andExpect(content().string(containsString(approvedName)));

		mockMvc.perform(admin.authorize(get("/api/admin/skill-suggestions")
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
	void adminApprovesSkillSuggestionAndCreatesCatalogSkill() throws Exception {
		AuthenticatedTestUser submitter = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser admin = registerAdmin();
		String suggestionName = "Approved Skill Suggestion";
		String suggestionId = createSuggestion(submitter, suggestionName);
		String moderationUrl = "/api/admin/skill-suggestions/" + suggestionId;

		mockMvc.perform(admin.authorize(patch(moderationUrl + "/approve")))
				.andExpect(status().isNoContent());

		mockMvc.perform(submitter.authorize(get("/api/skill-suggestions/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].suggestedByUserId").value(submitter.userId()))
				.andExpect(jsonPath("$[0].status").value("APPROVED"))
				.andExpect(jsonPath("$[0].reviewedAt").isNotEmpty())
				.andExpect(jsonPath("$[0].reviewedByUserId").value(admin.userId()));

		mockMvc.perform(submitter.authorize(get("/api/skills")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(suggestionName)));

		mockMvc.perform(admin.authorize(patch(moderationUrl + "/reject")))
				.andExpect(status().isConflict());
	}

	@Test
	void adminRejectsSkillSuggestionWithoutCreatingCatalogSkill() throws Exception {
		AuthenticatedTestUser submitter = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser admin = registerAdmin();
		String suggestionName = "Rejected Skill Suggestion";
		String suggestionId = createSuggestion(submitter, suggestionName);

		mockMvc.perform(admin.authorize(patch(
						"/api/admin/skill-suggestions/" + suggestionId + "/reject")))
				.andExpect(status().isNoContent());

		mockMvc.perform(submitter.authorize(get("/api/skill-suggestions/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("REJECTED"))
				.andExpect(jsonPath("$[0].reviewedByUserId").value(admin.userId()));

		mockMvc.perform(submitter.authorize(get("/api/skills")))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString(suggestionName))));
	}

	@Test
	void adminModerationReturnsNotFoundForMissingSkillSuggestion() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();

		mockMvc.perform(admin.authorize(patch(
						"/api/admin/skill-suggestions/00000000-0000-0000-0000-000000000000/approve")))
				.andExpect(status().isNotFound());
	}

	private String createSuggestion(AuthenticatedTestUser user, String name) throws Exception {
		String response = mockMvc.perform(user.authorize(post("/api/skill-suggestions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "Created for skill suggestion tests"
								}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		return objectMapper.readTree(response).path("id").asText();
	}

	private void createSkill(String name) throws Exception {
		AuthenticatedTestUser admin = registerAdmin();

		mockMvc.perform(admin.authorize(post("/api/skills"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "Created for skill suggestion tests"
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
