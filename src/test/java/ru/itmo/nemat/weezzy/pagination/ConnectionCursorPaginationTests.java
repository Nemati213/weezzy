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
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ConnectionCursorPaginationTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");
	private static final LocalDateTime FIXED_CREATED_AT =
			LocalDateTime.of(2026, 8, 2, 12, 0);

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
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void votesUseTieBreakerAndDoNotDuplicateEntriesAcrossPages() throws Exception {
		TestProfile source = createProfile("Cursor Vote Source");
		Set<String> expectedIds = new HashSet<>();

		for (int index = 0; index < 3; index++) {
			TestProfile target = createProfile("Cursor Vote Target " + index);
			expectedIds.add(target.id());
			performVote(source, target).andExpect(status().isOk());
		}

		jdbcTemplate.update(
				"UPDATE profile_votes SET created_at = ? WHERE source_profile_id = ?",
				Timestamp.valueOf(FIXED_CREATED_AT),
				UUID.fromString(source.id())
		);

		assertTwoPagesContainExactly(
				source,
				"/api/votes",
				"targetProfileId",
				expectedIds
		);
	}

	@Test
	void blocksUseTieBreakerAndDoNotDuplicateEntriesAcrossPages() throws Exception {
		TestProfile blocker = createProfile("Cursor Block Source");
		Set<String> expectedIds = new HashSet<>();

		for (int index = 0; index < 3; index++) {
			TestProfile blocked = createProfile("Cursor Block Target " + index);
			expectedIds.add(blocked.id());
			mockMvc.perform(blocker.owner().authorize(post("/api/blocks/" + blocked.id())))
					.andExpect(status().isOk());
		}

		jdbcTemplate.update(
				"UPDATE profile_blocks SET created_at = ? WHERE blocker_profile_id = ?",
				Timestamp.valueOf(FIXED_CREATED_AT),
				UUID.fromString(blocker.id())
		);

		assertTwoPagesContainExactly(
				blocker,
				"/api/blocks",
				"blockedProfile.id",
				expectedIds
		);
	}

	@Test
	void matchesUseCompositeTieBreakerAndDoNotDuplicateEntriesAcrossPages()
			throws Exception {
		TestProfile source = createProfile("Cursor Match Source");
		Set<String> expectedIds = new HashSet<>();

		for (int index = 0; index < 3; index++) {
			TestProfile matched = createProfile("Cursor Match Target " + index);
			expectedIds.add(matched.id());
			performVote(source, matched).andExpect(status().isOk());
			performVote(matched, source).andExpect(status().isOk());
		}

		jdbcTemplate.update(
				"""
				UPDATE profile_matches
				SET created_at = ?
				WHERE first_profile_id = ? OR second_profile_id = ?
				""",
				Timestamp.valueOf(FIXED_CREATED_AT),
				UUID.fromString(source.id()),
				UUID.fromString(source.id())
		);

		assertTwoPagesContainExactly(
				source,
				"/api/matches",
				"matchedProfile.id",
				expectedIds
		);
	}

	@Test
	void cursorEndpointsRejectInvalidCursorAndOutOfRangeLimit() throws Exception {
		TestProfile profile = createProfile("Cursor Boundary Profile");

		for (String endpoint : Set.of("/api/votes", "/api/blocks", "/api/matches")) {
			mockMvc.perform(profile.owner().authorize(get(endpoint))
						.param("cursor", "not-a-cursor"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message", containsString("cursor")));
			mockMvc.perform(profile.owner().authorize(get(endpoint))
						.param("limit", "0"))
					.andExpect(status().isBadRequest());
			mockMvc.perform(profile.owner().authorize(get(endpoint))
						.param("limit", "101"))
					.andExpect(status().isBadRequest());
		}
	}

	private void assertTwoPagesContainExactly(
			TestProfile source,
			String endpoint,
			String idPath,
			Set<String> expectedIds
	) throws Exception {
		String firstBody = mockMvc.perform(source.owner().authorize(get(endpoint))
						.param("limit", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.nextCursor").isNotEmpty())
				.andReturn()
				.getResponse()
				.getContentAsString();

		var firstJson = objectMapper.readTree(firstBody);
		String cursor = firstJson.path("nextCursor").asText();
		Set<String> firstIds = ids(firstJson.path("content"), idPath);

		String secondBody = mockMvc.perform(source.owner().authorize(get(endpoint))
						.param("limit", "2")
						.param("cursor", cursor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.nextCursor").doesNotExist())
				.andReturn()
				.getResponse()
				.getContentAsString();

		Set<String> secondIds = ids(
				objectMapper.readTree(secondBody).path("content"),
				idPath
		);
		assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
		firstIds.addAll(secondIds);
		assertThat(firstIds).containsExactlyInAnyOrderElementsOf(expectedIds);
	}

	private Set<String> ids(tools.jackson.databind.JsonNode content, String idPath) {
		Set<String> ids = new HashSet<>();
		for (int index = 0; index < content.size(); index++) {
			var value = content.get(index);
			for (String pathPart : idPath.split("\\.")) {
				value = value.path(pathPart);
			}
			ids.add(value.asText());
		}
		return ids;
	}

	private TestProfile createProfile(String displayName) throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile(displayName);
	}

	private org.springframework.test.web.servlet.ResultActions performVote(
			TestProfile source,
			TestProfile target
	) throws Exception {
		return mockMvc.perform(source.owner().authorize(post("/api/votes/" + target.id()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "action": "LIKE"
						}
						"""));
	}
}
