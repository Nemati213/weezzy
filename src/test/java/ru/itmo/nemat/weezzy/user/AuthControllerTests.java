package ru.itmo.nemat.weezzy.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

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
	void registerReturnsCreatedUserWithoutPasswordHash() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "AuthRegister@ITMO.ru",
								  "password": "password123"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.user.id").isNotEmpty())
				.andExpect(jsonPath("$.user.email").value("authregister@itmo.ru"))
				.andExpect(jsonPath("$.user.role").value("USER"))
				.andExpect(jsonPath("$.user.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
		register("duplicate-auth@itmo.ru", "password123")
				.andExpect(status().isCreated());

		register("DUPLICATE-AUTH@ITMO.RU", "password456")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value("User already exists: duplicate-auth@itmo.ru"));
	}

	@Test
	void registerRejectsInvalidEmail() throws Exception {
		register("not-an-email", "password123")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("email")));
	}

	@Test
	void registerRejectsShortPassword() throws Exception {
		register("short-password@itmo.ru", "123")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("password")));
	}

	@Test
	void loginReturnsUserForCorrectPassword() throws Exception {
		register("login-user@itmo.ru", "password123")
				.andExpect(status().isCreated());

		login("LOGIN-USER@ITMO.RU", "password123")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.user.id").isNotEmpty())
				.andExpect(jsonPath("$.user.email").value("login-user@itmo.ru"))
				.andExpect(jsonPath("$.user.role").value("USER"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void loginRejectsWrongPassword() throws Exception {
		register("wrong-login@itmo.ru", "password123")
				.andExpect(status().isCreated());

		login("wrong-login@itmo.ru", "password456")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void loginRejectsMissingUserAsInvalidCredentials() throws Exception {
		login("missing-user@itmo.ru", "password123")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void meReturnsCurrentUserForValidToken() throws Exception {
		String token = registerAndGetToken("me-user@itmo.ru", "password123");

		mockMvc.perform(get("/api/auth/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.email").value("me-user@itmo.ru"))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void meRejectsMissingToken() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().exists("X-Request-ID"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Authentication is required"))
				.andExpect(jsonPath("$.path").value("/api/auth/me"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void authorizationFailureUsesSameErrorFormat() throws Exception {
		String token = registerAndGetToken("forbidden-user@itmo.ru", "password123");

		mockMvc.perform(post("/api/goals")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Forbidden goal",
								  "description": "Regular users cannot create goals"
								}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(header().exists("X-Request-ID"))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.error").value("Forbidden"))
				.andExpect(jsonPath("$.message").value("Access is denied"))
				.andExpect(jsonPath("$.path").value("/api/goals"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void requestIdIsPropagatedThroughApiErrors() throws Exception {
		String requestId = "mobile-request-42";

		mockMvc.perform(post("/api/auth/register")
						.header("X-Request-ID", requestId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "invalid-email",
								  "password": "password123"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(header().string("X-Request-ID", requestId))
				.andExpect(jsonPath("$.requestId").value(requestId));
	}

	@Test
	void actuatorHealthProbesArePublic() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));

		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void actuatorMetricsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/actuator/metrics"))
				.andExpect(status().isUnauthorized());

		String token = registerAndGetToken("metrics-user@itmo.ru", "password123");
		mockMvc.perform(get("/actuator/metrics")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.names", hasItem("http.server.requests")));

		mockMvc.perform(get("/actuator/prometheus")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void openApiDocumentationIsPublicAndContainsBearerAuthentication() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Weezzy API"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type")
						.value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
						.value("bearer"))
				.andExpect(jsonPath("$.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/auth/login'].post.security")
						.doesNotExist())
				.andExpect(jsonPath(
						"$.paths['/api/recommendations'].get.security[0].bearerAuth"
				).isArray())
				.andExpect(jsonPath("$.paths['/api/recommendations']").exists());
	}

	@Test
	void swaggerUiIsPublic() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection());
	}

	private ResultActions register(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "%s"
						}
						""".formatted(email, password)));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "%s"
						}
						""".formatted(email, password)));
	}

	private String registerAndGetToken(String email, String password) throws Exception {
		String response = register(email, password)
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode json = objectMapper.readTree(response);

		return json.path("accessToken").asText();
	}
}
