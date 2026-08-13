package ru.itmo.nemat.weezzy.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.outbox.payload.ProfileLikedPayload;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OutboxEventPublishingTests {
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
	private OutboxEventRepository eventRepository;

	@Autowired
	private OutboxEventService eventService;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void firstLikePublishesPendingProfileLikedEvent() throws Exception {
		TestProfile source = createProfile("Outbox Like Source");
		TestProfile target = createProfile("Outbox Like Target");

		performVote(source, target, "LIKE");

		OutboxEvent event = findEvents(
				OutboxEventType.PROFILE_LIKED,
				"sourceProfileId",
				source.id()
		).getFirst();
		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(event.getAttemptCount()).isZero();
		assertThat(event.getNextAttemptAt()).isNotNull();
		assertThat(event.getPayload()).containsOnlyKeys(
				"sourceProfileId",
				"targetProfileId",
				"recipientUserId"
		);
		assertThat(value(event, "targetProfileId")).isEqualTo(target.id());
		assertThat(value(event, "recipientUserId"))
				.isEqualTo(target.owner().userId());
	}

	@Test
	void repeatedLikeAndPassDoNotPublishAdditionalEvents() throws Exception {
		TestProfile source = createProfile("Outbox Repeat Source");
		TestProfile target = createProfile("Outbox Repeat Target");

		performVote(source, target, "LIKE");
		performVote(source, target, "LIKE");
		performVote(source, target, "PASS");

		assertThat(findEvents(
				OutboxEventType.PROFILE_LIKED,
				"sourceProfileId",
				source.id()
		)).hasSize(1);
		assertThat(findEvents(
				OutboxEventType.MATCH_CREATED,
				"firstProfileId",
				source.id()
		)).isEmpty();
	}

	@Test
	void reciprocalLikePublishesOneMatchEventWithoutSecondLikeEvent() throws Exception {
		TestProfile first = createProfile("Outbox Match First");
		TestProfile second = createProfile("Outbox Match Second");

		performVote(first, second, "LIKE");
		performVote(second, first, "LIKE");

		List<OutboxEvent> likeEvents = eventRepository.findAll().stream()
				.filter(event -> event.getEventType() == OutboxEventType.PROFILE_LIKED)
				.filter(event -> Set.of(first.id(), second.id()).contains(
						value(event, "sourceProfileId")
				))
				.toList();
		assertThat(likeEvents).hasSize(1);
		assertThat(value(likeEvents.getFirst(), "sourceProfileId")).isEqualTo(first.id());

		OutboxEvent matchEvent = eventRepository.findAll().stream()
				.filter(event -> event.getEventType() == OutboxEventType.MATCH_CREATED)
				.filter(event -> Set.of(first.id(), second.id()).contains(
						value(event, "firstProfileId")
				))
				.findFirst()
				.orElseThrow();
		assertThat(matchEvent.getPayload()).containsOnlyKeys(
				"firstProfileId",
				"firstUserId",
				"secondProfileId",
				"secondUserId"
		);
		assertThat(value(matchEvent, "firstProfileId")).isEqualTo(second.id());
		assertThat(value(matchEvent, "firstUserId")).isEqualTo(second.owner().userId());
		assertThat(value(matchEvent, "secondProfileId")).isEqualTo(first.id());
		assertThat(value(matchEvent, "secondUserId")).isEqualTo(first.owner().userId());
	}

	@Test
	void outboxEventRollsBackWithPublishingTransaction() {
		UUID sourceProfileId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(status -> {
			eventService.publish(new ProfileLikedPayload(
					sourceProfileId,
					UUID.randomUUID(),
					UUID.randomUUID()
			));
			status.setRollbackOnly();
		});

		assertThat(findEvents(
				OutboxEventType.PROFILE_LIKED,
				"sourceProfileId",
				sourceProfileId.toString()
		)).isEmpty();
	}

	private TestProfile createProfile(String displayName) throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile(displayName);
	}

	private void performVote(TestProfile source, TestProfile target, String action)
			throws Exception {
		mockMvc.perform(source.owner().authorize(post("/api/votes/" + target.id()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "action": "%s"
								}
								""".formatted(action)))
				.andExpect(status().isOk());
	}

	private List<OutboxEvent> findEvents(
			OutboxEventType eventType,
			String payloadKey,
			String payloadValue
	) {
		return eventRepository.findAll().stream()
				.filter(event -> event.getEventType() == eventType)
				.filter(event -> payloadValue.equals(value(event, payloadKey)))
				.toList();
	}

	private String value(OutboxEvent event, String key) {
		return String.valueOf(event.getPayload().get(key));
	}
}
