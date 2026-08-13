package ru.itmo.nemat.weezzy.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");
	private static final LocalDateTime FIXED_CREATED_AT =
			LocalDateTime.of(2026, 8, 13, 12, 0);
	private static final String ENDPOINT = "/api/notifications/me";

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
	private NotificationService notificationService;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void getNotificationsReturnsOnlyCurrentUsersNotifications() throws Exception {
		AuthenticatedTestUser currentUser = registerUser();
		AuthenticatedTestUser otherUser = registerUser();
		UUID matchedProfileId = UUID.randomUUID();
		Notification ownNotification = createNotification(
				currentUser,
				NotificationType.NEW_MATCH,
				Map.of("matchedProfileId", matchedProfileId.toString())
		);
		Notification otherNotification = createNotification(
				otherUser,
				NotificationType.ADMIN_SANCTION,
				Map.of("sanctionId", UUID.randomUUID().toString())
		);

		mockMvc.perform(currentUser.authorize(get(ENDPOINT)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id")
						.value(ownNotification.getId().toString()))
				.andExpect(jsonPath("$.content[0].type").value("NEW_MATCH"))
				.andExpect(jsonPath("$.content[0].payload.matchedProfileId")
						.value(matchedProfileId.toString()))
				.andExpect(jsonPath("$.content[0].readAt").doesNotExist())
				.andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
				.andExpect(content().string(not(containsString(
						otherNotification.getId().toString()
				))));
	}

	@Test
	void notificationsUseCursorWithoutDuplicates() throws Exception {
		AuthenticatedTestUser user = registerUser();
		Set<String> expectedIds = new HashSet<>();

		for (int index = 0; index < 3; index++) {
			expectedIds.add(createNotification(
					user,
					NotificationType.REPORT_DECISION,
					Map.of("reportId", UUID.randomUUID().toString())
			).getId().toString());
		}
		jdbcTemplate.update(
				"UPDATE notifications SET created_at = ? WHERE recipient_user_id = ?",
				Timestamp.valueOf(FIXED_CREATED_AT),
				UUID.fromString(user.userId())
		);

		String firstBody = mockMvc.perform(user.authorize(get(ENDPOINT))
						.param("limit", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.nextCursor").isNotEmpty())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode firstPage = objectMapper.readTree(firstBody);

		String secondBody = mockMvc.perform(user.authorize(get(ENDPOINT))
						.param("limit", "2")
						.param("cursor", firstPage.path("nextCursor").asText()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.nextCursor").doesNotExist())
				.andReturn()
				.getResponse()
				.getContentAsString();

		Set<String> firstIds = notificationIds(firstPage.path("content"));
		Set<String> secondIds = notificationIds(
				objectMapper.readTree(secondBody).path("content")
		);
		assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
		firstIds.addAll(secondIds);
		assertThat(firstIds).containsExactlyInAnyOrderElementsOf(expectedIds);
	}

	@Test
	void getNotificationsRejectsInvalidCursorAndLimit() throws Exception {
		AuthenticatedTestUser user = registerUser();

		mockMvc.perform(user.authorize(get(ENDPOINT)).param("cursor", "invalid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("cursor")));
		mockMvc.perform(user.authorize(get(ENDPOINT)).param("limit", "0"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(user.authorize(get(ENDPOINT)).param("limit", "101"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void markAsReadIsIdempotent() throws Exception {
		AuthenticatedTestUser user = registerUser();
		Notification notification = createNotification(
				user,
				NotificationType.NEW_MATCH,
				Map.of("matchedProfileId", UUID.randomUUID().toString())
		);
		String endpoint = ENDPOINT + "/" + notification.getId() + "/read";

		mockMvc.perform(user.authorize(patch(endpoint)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(notification.getId().toString()))
				.andExpect(jsonPath("$.readAt").isNotEmpty());
		LocalDateTime firstReadAt = notificationRepository.findById(notification.getId())
				.orElseThrow()
				.getReadAt();

		mockMvc.perform(user.authorize(patch(endpoint)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.readAt").isNotEmpty());
		LocalDateTime secondReadAt = notificationRepository.findById(notification.getId())
				.orElseThrow()
				.getReadAt();
		assertThat(secondReadAt).isEqualTo(firstReadAt);
	}

	@Test
	void markAsReadDoesNotExposeAnotherUsersNotification() throws Exception {
		AuthenticatedTestUser owner = registerUser();
		AuthenticatedTestUser otherUser = registerUser();
		Notification notification = createNotification(
				owner,
				NotificationType.NEW_MATCH,
				Map.of("matchedProfileId", UUID.randomUUID().toString())
		);

		mockMvc.perform(otherUser.authorize(patch(
				ENDPOINT + "/" + notification.getId() + "/read"
		)))
				.andExpect(status().isNotFound());
	}

	@Test
	void markAllAsReadUpdatesOnlyCurrentUsersUnreadNotifications() throws Exception {
		AuthenticatedTestUser currentUser = registerUser();
		AuthenticatedTestUser otherUser = registerUser();
		Notification first = createNotification(
				currentUser,
				NotificationType.NEW_MATCH,
				Map.of("matchedProfileId", UUID.randomUUID().toString())
		);
		Notification second = createNotification(
				currentUser,
				NotificationType.REPORT_DECISION,
				Map.of("reportId", UUID.randomUUID().toString())
		);
		Notification other = createNotification(
				otherUser,
				NotificationType.ADMIN_SANCTION,
				Map.of("sanctionId", UUID.randomUUID().toString())
		);

		mockMvc.perform(currentUser.authorize(patch(ENDPOINT + "/read-all")))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		assertThat(notificationRepository.findById(first.getId()).orElseThrow().getReadAt())
				.isNotNull();
		assertThat(notificationRepository.findById(second.getId()).orElseThrow().getReadAt())
				.isNotNull();
		assertThat(notificationRepository.findById(other.getId()).orElseThrow().getReadAt())
				.isNull();
	}

	@Test
	void notificationEndpointsRequireAuthentication() throws Exception {
		UUID notificationId = UUID.randomUUID();

		mockMvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
		mockMvc.perform(patch(ENDPOINT + "/" + notificationId + "/read"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(patch(ENDPOINT + "/read-all"))
				.andExpect(status().isUnauthorized());
	}

	private AuthenticatedTestUser registerUser() throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper);
	}

	private Notification createNotification(
			AuthenticatedTestUser user,
			NotificationType type,
			Map<String, Object> payload
	) {
		return notificationService.createIfAbsent(
				UUID.fromString(user.userId()),
				type,
				payload,
				UUID.randomUUID()
		);
	}

	private Set<String> notificationIds(JsonNode content) {
		Set<String> ids = new HashSet<>();
		for (int index = 0; index < content.size(); index++) {
			ids.add(content.get(index).path("id").asText());
		}
		return ids;
	}
}
