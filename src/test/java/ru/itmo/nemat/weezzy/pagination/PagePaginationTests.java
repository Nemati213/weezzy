package ru.itmo.nemat.weezzy.pagination;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PagePaginationTests {
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

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void profilesReturnStablePageMetadataWithoutDuplicates() throws Exception {
		Set<String> expectedIds = new HashSet<>();
		TestProfile requester = null;

		for (int index = 0; index < 3; index++) {
			TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper)
					.createProfile("Page Profile " + index);
			expectedIds.add(profile.id());
			requester = profile;
		}

		jdbcTemplate.update(
				"UPDATE profiles SET created_at = ?",
				Timestamp.valueOf(LocalDateTime.of(2026, 8, 2, 13, 0))
		);

		String firstBody = mockMvc.perform(requester.owner().authorize(get("/api/profiles"))
						.param("page", "0")
						.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.hasNext").value(true))
				.andExpect(jsonPath("$.hasPrevious").value(false))
				.andExpect(jsonPath("$.content[*].telegram").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		String secondBody = mockMvc.perform(requester.owner().authorize(get("/api/profiles"))
						.param("page", "1")
						.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.hasNext").value(false))
				.andExpect(jsonPath("$.hasPrevious").value(true))
				.andReturn().getResponse().getContentAsString();

		Set<String> firstIds = contentIds(firstBody);
		Set<String> secondIds = contentIds(secondBody);
		assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
		firstIds.addAll(secondIds);
		assertThat(firstIds).containsExactlyInAnyOrderElementsOf(expectedIds);
	}

	@Test
	void catalogsUseCommonPageResponseAndStableSorting() throws Exception {
		AuthenticatedTestUser admin = AuthenticatedTestUser.registerAdmin(
				mockMvc,
				objectMapper,
				userRepository,
				jwtService
		);
		createCatalogItems(admin);

		for (String endpoint : List.of("/api/skills", "/api/interests", "/api/goals")) {
			String firstBody = mockMvc.perform(admin.authorize(get(endpoint))
							.param("page", "0")
							.param("size", "2"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.content.length()").value(2))
						.andExpect(jsonPath("$.page").value(0))
						.andExpect(jsonPath("$.size").value(2))
						.andExpect(jsonPath("$.hasNext").value(true))
						.andExpect(jsonPath("$.hasPrevious").value(false))
						.andReturn().getResponse().getContentAsString();
			String secondBody = mockMvc.perform(admin.authorize(get(endpoint))
							.param("page", "1")
							.param("size", "2"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.content").isArray())
						.andExpect(jsonPath("$.page").value(1))
						.andExpect(jsonPath("$.hasPrevious").value(true))
						.andReturn().getResponse().getContentAsString();

			assertThat(contentIds(firstBody))
					.doesNotContainAnyElementsOf(contentIds(secondBody));
		}
	}

	@Test
	void pageEndpointsRejectOutOfRangeParameters() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		for (String endpoint : List.of(
				"/api/profiles",
				"/api/skills",
				"/api/interests",
				"/api/goals"
		)) {
			mockMvc.perform(user.authorize(get(endpoint)).param("page", "-1"))
					.andExpect(status().isBadRequest());
			mockMvc.perform(user.authorize(get(endpoint)).param("size", "0"))
					.andExpect(status().isBadRequest());
			mockMvc.perform(user.authorize(get(endpoint)).param("size", "101"))
					.andExpect(status().isBadRequest());
		}
	}

	private void createCatalogItems(AuthenticatedTestUser admin) throws Exception {
		for (int index = 0; index < 3; index++) {
			createCatalogItem(admin, "/api/skills", """
					{
					  "name": "Page Skill %d"
					}
					""".formatted(index));
			createCatalogItem(admin, "/api/interests", """
					{
					  "name": "Page Interest %d"
					}
					""".formatted(index));
			createCatalogItem(admin, "/api/goals", """
					{
					  "code": "PAGE_GOAL_%d",
					  "name": "Page Goal %d"
					}
					""".formatted(index, index));
		}
	}

	private void createCatalogItem(
			AuthenticatedTestUser admin,
			String endpoint,
			String body
	) throws Exception {
		mockMvc.perform(admin.authorize(post(endpoint))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isCreated());
	}

	private Set<String> contentIds(String body) throws Exception {
		var content = objectMapper.readTree(body).path("content");
		Set<String> ids = new HashSet<>();
		for (int index = 0; index < content.size(); index++) {
			ids.add(content.get(index).path("id").asText());
		}
		return ids;
	}
}
