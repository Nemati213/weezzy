package ru.itmo.nemat.weezzy.lunch.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.location.Location;
import ru.itmo.nemat.weezzy.location.LocationRepository;
import ru.itmo.nemat.weezzy.location.LocationType;
import ru.itmo.nemat.weezzy.location.University;
import ru.itmo.nemat.weezzy.location.UniversityRepository;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroup;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMember;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMemberId;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMemberRepository;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupRepository;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupStatus;
import ru.itmo.nemat.weezzy.lunch.group.lifecycle.LunchGroupLifecycleService;
import ru.itmo.nemat.weezzy.lunch.chat.dto.CreateLunchChatMessageRequest;
import ru.itmo.nemat.weezzy.lunch.config.LunchProperties;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestStatus;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.common.pagination.CursorTokenCodec;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false",
		"app.lunch.lifecycle.enabled=false",
		"app.outbox.worker.enabled=false",
		"app.outbox.cleanup.enabled=false"
})
class LunchChatMessageControllerTests {
	private static final String MESSAGES_PATH = "/api/lunch/groups/me/messages";
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
	private UniversityRepository universityRepository;

	@Autowired
	private LocationRepository locationRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private LunchRequestRepository requestRepository;

	@Autowired
	private LunchGroupRepository groupRepository;

	@Autowired
	private LunchGroupMemberRepository memberRepository;

	@Autowired
	private LunchChatMessageRepository messageRepository;

	@Autowired
	private CursorTokenCodec cursorTokenCodec;

	@Autowired
	private LunchChatService chatService;

	@Autowired
	private LunchGroupLifecycleService lifecycleService;

	@Autowired
	private LunchProperties lunchProperties;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Location location;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void setUp() {
		location = createLocation();
	}

	@Test
	void currentMemberCanSendMessageWithoutPrivateDataExposure() throws Exception {
		Participant participant = createParticipant("Sender");
		LunchGroup group = createGroup();
		createMember(group, participant.profile());
		UUID clientMessageId = UUID.randomUUID();

		mockMvc.perform(participant.user().authorize(post(MESSAGES_PATH))
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody(clientMessageId, "  Meet near the entrance  ")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.groupId").value(group.getId().toString()))
				.andExpect(jsonPath("$.senderProfileId")
						.value(participant.profile().getId().toString()))
				.andExpect(jsonPath("$.senderDisplayName")
						.value(participant.profile().getDisplayName()))
				.andExpect(jsonPath("$.content").value("Meet near the entrance"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.telegram").doesNotExist())
				.andExpect(jsonPath("$.userId").doesNotExist())
				.andExpect(jsonPath("$.email").doesNotExist())
				.andExpect(jsonPath("$.clientMessageId").doesNotExist());

		assertThat(messageRepository.countByGroupIdAndSenderProfileId(
				group.getId(),
				participant.profile().getId()
		)).isEqualTo(1);
	}

	@Test
	void retryReturnsSameMessageWithoutCreatingDuplicate() throws Exception {
		Participant participant = createParticipant("Sender");
		LunchGroup group = createGroup();
		createMember(group, participant.profile());
		UUID clientMessageId = UUID.randomUUID();
		String body = requestBody(clientMessageId, "Same message");

		MvcResult first = mockMvc.perform(participant.user().authorize(post(MESSAGES_PATH))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isCreated())
				.andReturn();
		MvcResult retry = mockMvc.perform(participant.user().authorize(post(MESSAGES_PATH))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isOk())
				.andReturn();

		assertThat(responseId(retry)).isEqualTo(responseId(first));
		assertThat(messageRepository.countByGroupIdAndSenderProfileId(
				group.getId(),
				participant.profile().getId()
		)).isEqualTo(1);
	}

	@Test
	void reusedClientMessageIdWithDifferentContentReturnsConflict() throws Exception {
		Participant participant = createParticipant("Sender");
		LunchGroup group = createGroup();
		createMember(group, participant.profile());
		UUID clientMessageId = UUID.randomUUID();

		mockMvc.perform(participant.user().authorize(post(MESSAGES_PATH))
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody(clientMessageId, "Original")))
				.andExpect(status().isCreated());
		mockMvc.perform(participant.user().authorize(post(MESSAGES_PATH))
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody(clientMessageId, "Changed")))
				.andExpect(status().isConflict());

		assertThat(messageRepository.countByGroupIdAndSenderProfileId(
				group.getId(),
				participant.profile().getId()
		)).isEqualTo(1);
	}

	@Test
	void concurrentRetryCreatesExactlyOneMessage() throws Exception {
		Participant participant = createParticipant("Sender");
		LunchGroup group = createGroup();
		createMember(group, participant.profile());
		String body = requestBody(UUID.randomUUID(), "Concurrent message");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<Integer> first = executor.submit(() -> sendAfterSignal(
					participant.user(),
					body,
					ready,
					start
			));
			Future<Integer> second = executor.submit(() -> sendAfterSignal(
					participant.user(),
					body,
					ready,
					start
			));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(List.of(
					first.get(30, TimeUnit.SECONDS),
					second.get(30, TimeUnit.SECONDS)
			)).containsExactlyInAnyOrder(
					HttpStatus.CREATED.value(),
					HttpStatus.OK.value()
			);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(messageRepository.countByGroupIdAndSenderProfileId(
				group.getId(),
				participant.profile().getId()
		)).isEqualTo(1);
	}

	@Test
	void concurrentCloseWinsBeforeMessageIsPersisted() throws Exception {
		Participant participant = createParticipant("Closing race sender");
		LunchGroup group = createGroup();
		createMember(group, participant.profile());
		CountDownLatch groupLocked = new CountDownLatch(1);
		CountDownLatch allowClose = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<Void> close = executor.submit(() -> {
				closeGroupWhileHoldingLock(group.getId(), groupLocked, allowClose);
				return null;
			});
			assertThat(groupLocked.await(5, TimeUnit.SECONDS)).isTrue();
			Future<Integer> send = executor.submit(() -> sendStatus(
					participant.user(),
					requestBody(UUID.randomUUID(), "Too late")
			));

			assertThatThrownBy(() -> send.get(500, TimeUnit.MILLISECONDS))
					.isInstanceOf(TimeoutException.class);
			allowClose.countDown();
			close.get(30, TimeUnit.SECONDS);
			assertThat(send.get(30, TimeUnit.SECONDS))
					.isEqualTo(HttpStatus.NOT_FOUND.value());
		} finally {
			allowClose.countDown();
			executor.shutdownNow();
		}

		assertThat(messageRepository.countByGroupIdAndSenderProfileId(
				group.getId(),
				participant.profile().getId()
		)).isZero();
	}

	@Test
	void lifecycleSkipsGroupUntilConcurrentMessageCommits() throws Exception {
		Participant participant = createParticipant("Sending race sender");
		LunchGroup group = createGroup();
		createMember(group, participant.profile());
		CountDownLatch messageSaved = new CountDownLatch(1);
		CountDownLatch allowCommit = new CountDownLatch(1);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		LocalDateTime completionTime = group.getTimeSlot()
				.plus(lunchProperties.groupDuration());

		try {
			Future<LunchChatSendResult> send = executor.submit(() ->
					sendWhileHoldingGroupLock(
							UUID.fromString(participant.user().userId()),
							messageSaved,
							allowCommit
					));
			assertThat(messageSaved.await(5, TimeUnit.SECONDS)).isTrue();

			assertThat(lifecycleService.completeDueGroups(completionTime, 100))
					.doesNotContain(group.getId());
			allowCommit.countDown();
			assertThat(send.get(30, TimeUnit.SECONDS).created()).isTrue();
		} finally {
			allowCommit.countDown();
			executor.shutdownNow();
		}

		assertThat(lifecycleService.completeDueGroups(completionTime, 100))
				.containsExactly(group.getId());
		assertThat(messageRepository.countByGroupIdAndSenderProfileId(
				group.getId(),
				participant.profile().getId()
		)).isEqualTo(1);
	}

	@Test
	void outsiderAndAnonymousUserCannotSendMessage() throws Exception {
		Participant member = createParticipant("Member");
		Participant outsider = createParticipant("Outsider");
		LunchGroup group = createGroup();
		createMember(group, member.profile());
		String body = requestBody(UUID.randomUUID(), "Hello");

		mockMvc.perform(outsider.user().authorize(post(MESSAGES_PATH))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message")
						.value("Active lunch group not found"))
				.andExpect(jsonPath("$.message")
						.value(not(containsString(outsider.user().userId()))));
		mockMvc.perform(post(MESSAGES_PATH)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void completedAndCancelledGroupsRejectMessages() throws Exception {
		Participant completedMember = createParticipant("Completed");
		LunchGroup completedGroup = createGroup();
		createMember(completedGroup, completedMember.profile());
		completedGroup.setStatus(LunchGroupStatus.COMPLETED);
		completedGroup.setCompletedAt(LocalDateTime.now());
		groupRepository.saveAndFlush(completedGroup);

		Participant cancelledMember = createParticipant("Cancelled");
		LunchGroup cancelledGroup = createGroup();
		createMember(cancelledGroup, cancelledMember.profile());
		cancelledGroup.setStatus(LunchGroupStatus.CANCELLED);
		cancelledGroup.setCancelledAt(LocalDateTime.now());
		groupRepository.saveAndFlush(cancelledGroup);

		mockMvc.perform(completedMember.user().authorize(post(MESSAGES_PATH))
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody(UUID.randomUUID(), "Too late")))
				.andExpect(status().isNotFound());
		mockMvc.perform(cancelledMember.user().authorize(post(MESSAGES_PATH))
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody(UUID.randomUUID(), "Too late")))
				.andExpect(status().isNotFound());
	}

	@Test
	void releasedMemberCannotSendMessage() throws Exception {
		Participant participant = createParticipant("Released");
		LunchGroup group = createGroup();
		LunchGroupMember membership = createMember(group, participant.profile());
		membership.setReleasedAt(LocalDateTime.now());
		memberRepository.saveAndFlush(membership);

		mockMvc.perform(participant.user().authorize(post(MESSAGES_PATH))
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody(UUID.randomUUID(), "Too late")))
				.andExpect(status().isNotFound());
	}

	@Test
	void invalidMessageBodyReturnsBadRequest() throws Exception {
		Participant participant = createParticipant("Sender");
		LunchGroup group = createGroup();
		createMember(group, participant.profile());

		List<Map<String, Object>> invalidBodies = List.of(
				Map.of(),
				Map.of("content", "Hello"),
				Map.of("clientMessageId", UUID.randomUUID()),
				Map.of(
						"clientMessageId",
						"not-a-uuid",
						"content",
						"Hello"
				),
				Map.of(
						"clientMessageId",
						UUID.randomUUID(),
						"content",
						" \t "
				),
				Map.of(
						"clientMessageId",
						UUID.randomUUID(),
						"content",
						"a".repeat(LunchChatMessage.MAX_CONTENT_LENGTH + 1)
				)
		);

		for (Map<String, Object> body : invalidBodies) {
			mockMvc.perform(participant.user().authorize(post(MESSAGES_PATH))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
					.andExpect(status().isBadRequest());
		}
	}

	@Test
	void initialAndBeforePagesAreStableWithEqualTimestamps() throws Exception {
		Participant participant = createParticipant("Reader");
		LunchGroup group = createGroup();
		createMember(group, participant.profile());

		JsonNode emptyPage = findPage(participant.user(), null, null, 2);
		assertThat(messageIds(emptyPage)).isEmpty();
		assertThat(emptyPage.path("nextBeforeCursor").isNull()).isTrue();
		assertThat(emptyPage.path("nextAfterCursor").isNull()).isTrue();

		for (int index = 0; index < 5; index++) {
			sendMessage(participant.user(), "Message " + index);
		}
		LocalDateTime createdAt = LocalDateTime.of(2026, 8, 23, 13, 0);
		setGroupMessageTime(group.getId(), createdAt);
		List<UUID> persistedOrder = messageIdsInDatabase(group.getId());

		JsonNode initial = findPage(participant.user(), null, null, 2);
		assertThat(messageIds(initial)).containsExactlyElementsOf(
				persistedOrder.subList(3, 5)
		);
		JsonNode firstMessage = initial.path("content").get(0);
		assertThat(firstMessage.has("telegram")).isFalse();
		assertThat(firstMessage.has("email")).isFalse();
		assertThat(firstMessage.has("userId")).isFalse();
		assertThat(firstMessage.has("clientMessageId")).isFalse();
		String firstBefore = initial.path("nextBeforeCursor").asText();
		assertThat(firstBefore).isNotBlank();
		assertThat(initial.path("nextAfterCursor").asText()).isNotBlank();

		JsonNode secondPage = findPage(participant.user(), firstBefore, null, 2);
		assertThat(messageIds(secondPage)).containsExactlyElementsOf(
				persistedOrder.subList(1, 3)
		);
		String secondBefore = secondPage.path("nextBeforeCursor").asText();
		assertThat(secondBefore).isNotBlank();
		assertThat(secondPage.path("nextAfterCursor").isNull()).isTrue();

		JsonNode lastPage = findPage(participant.user(), secondBefore, null, 2);
		assertThat(messageIds(lastPage)).containsExactly(persistedOrder.getFirst());
		assertThat(lastPage.path("nextBeforeCursor").isNull()).isTrue();
	}

	@Test
	void afterCursorPollsNewMessagesWithoutDuplicates() throws Exception {
		Participant participant = createParticipant("Reader");
		LunchGroup group = createGroup();
		createMember(group, participant.profile());
		UUID initialFirst = sendMessage(participant.user(), "Initial first");
		UUID initialSecond = sendMessage(participant.user(), "Initial second");
		LocalDateTime initialTime = LocalDateTime.of(2026, 8, 23, 13, 0);
		setMessageTime(initialFirst, initialTime);
		setMessageTime(initialSecond, initialTime);

		JsonNode initial = findPage(participant.user(), null, null, 10);
		String syncCursor = initial.path("nextAfterCursor").asText();
		assertThat(syncCursor).isNotBlank();

		UUID newFirst = sendMessage(participant.user(), "New first");
		UUID newSecond = sendMessage(participant.user(), "New second");
		LocalDateTime newTime = initialTime.plusMinutes(1);
		setMessageTime(newFirst, newTime);
		setMessageTime(newSecond, newTime);
		List<UUID> expectedNewOrder = messageIdsAtTime(group.getId(), newTime);

		JsonNode firstPoll = findPage(participant.user(), null, syncCursor, 1);
		assertThat(messageIds(firstPoll)).containsExactly(expectedNewOrder.getFirst());
		assertThat(firstPoll.path("nextBeforeCursor").isNull()).isTrue();
		String firstPollCursor = firstPoll.path("nextAfterCursor").asText();

		JsonNode secondPoll = findPage(
				participant.user(),
				null,
				firstPollCursor,
				1
		);
		assertThat(messageIds(secondPoll)).containsExactly(expectedNewOrder.getLast());
		String latestCursor = secondPoll.path("nextAfterCursor").asText();

		JsonNode emptyPoll = findPage(participant.user(), null, latestCursor, 10);
		assertThat(messageIds(emptyPoll)).isEmpty();
		assertThat(emptyPoll.path("nextAfterCursor").asText())
				.isEqualTo(latestCursor);
	}

	@Test
	void invalidPaginationParametersReturnBadRequest() throws Exception {
		Participant participant = createParticipant("Reader");
		LunchGroup group = createGroup();
		createMember(group, participant.profile());

		mockMvc.perform(participant.user().authorize(get(MESSAGES_PATH))
					.queryParam("before", "first")
					.queryParam("after", "second"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(participant.user().authorize(get(MESSAGES_PATH))
					.queryParam("before", "invalid"))
				.andExpect(status().isBadRequest());
		String wrongCursorType = cursorTokenCodec.encode(
				"notification",
				List.of("value", "value", "value")
		);
		mockMvc.perform(participant.user().authorize(get(MESSAGES_PATH))
					.queryParam("before", wrongCursorType))
				.andExpect(status().isBadRequest());
		mockMvc.perform(participant.user().authorize(get(MESSAGES_PATH))
					.queryParam("limit", "0"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(participant.user().authorize(get(MESSAGES_PATH))
					.queryParam("limit", "101"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void cursorFromAnotherGroupIsRejected() throws Exception {
		Participant first = createParticipant("First reader");
		LunchGroup firstGroup = createGroup();
		createMember(firstGroup, first.profile());
		sendMessage(first.user(), "First group message");
		String foreignCursor = findPage(first.user(), null, null, 10)
				.path("nextAfterCursor")
				.asText();

		Participant second = createParticipant("Second reader");
		LunchGroup secondGroup = createGroup();
		createMember(secondGroup, second.profile());

		mockMvc.perform(second.user().authorize(get(MESSAGES_PATH))
					.queryParam("after", foreignCursor))
				.andExpect(status().isBadRequest());
	}

	@Test
	void onlyCurrentMembersOfActiveGroupCanReadMessages() throws Exception {
		Participant member = createParticipant("Member");
		Participant outsider = createParticipant("Outsider");
		LunchGroup group = createGroup();
		createMember(group, member.profile());
		sendMessage(member.user(), "Private group message");

		mockMvc.perform(outsider.user().authorize(get(MESSAGES_PATH)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message")
						.value("Active lunch group not found"));
		mockMvc.perform(get(MESSAGES_PATH))
				.andExpect(status().isUnauthorized());

		group.setStatus(LunchGroupStatus.COMPLETED);
		group.setCompletedAt(LocalDateTime.now());
		groupRepository.saveAndFlush(group);
		mockMvc.perform(member.user().authorize(get(MESSAGES_PATH)))
				.andExpect(status().isNotFound());

		Participant cancelled = createParticipant("Cancelled");
		LunchGroup cancelledGroup = createGroup();
		createMember(cancelledGroup, cancelled.profile());
		cancelledGroup.setStatus(LunchGroupStatus.CANCELLED);
		cancelledGroup.setCancelledAt(LocalDateTime.now());
		groupRepository.saveAndFlush(cancelledGroup);
		mockMvc.perform(cancelled.user().authorize(get(MESSAGES_PATH)))
				.andExpect(status().isNotFound());

		Participant released = createParticipant("Released");
		LunchGroup activeGroup = createGroup();
		LunchGroupMember membership = createMember(
				activeGroup,
				released.profile()
		);
		membership.setReleasedAt(LocalDateTime.now());
		memberRepository.saveAndFlush(membership);
		mockMvc.perform(released.user().authorize(get(MESSAGES_PATH)))
				.andExpect(status().isNotFound());
	}

	private JsonNode findPage(
			AuthenticatedTestUser user,
			String before,
			String after,
			int limit
	) throws Exception {
		MockHttpServletRequestBuilder request = get(MESSAGES_PATH)
				.queryParam("limit", Integer.toString(limit));
		if (before != null) {
			request.queryParam("before", before);
		}
		if (after != null) {
			request.queryParam("after", after);
		}
		MvcResult result = mockMvc.perform(user.authorize(request))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private List<UUID> messageIds(JsonNode page) {
		List<UUID> ids = new ArrayList<>();
		page.path("content").forEach(message -> ids.add(UUID.fromString(
				message.path("id").asText()
		)));
		return List.copyOf(ids);
	}

	private UUID sendMessage(AuthenticatedTestUser user, String content)
			throws Exception {
		MvcResult result = mockMvc.perform(user.authorize(post(MESSAGES_PATH))
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody(UUID.randomUUID(), content)))
				.andExpect(status().isCreated())
				.andReturn();
		return responseId(result);
	}

	private void setGroupMessageTime(UUID groupId, LocalDateTime createdAt) {
		jdbcTemplate.update(
				"UPDATE lunch_group_messages SET created_at = ? WHERE group_id = ?",
				createdAt,
				groupId
		);
	}

	private void setMessageTime(UUID messageId, LocalDateTime createdAt) {
		jdbcTemplate.update(
				"UPDATE lunch_group_messages SET created_at = ? WHERE id = ?",
				createdAt,
				messageId
		);
	}

	private List<UUID> messageIdsInDatabase(UUID groupId) {
		return jdbcTemplate.query(
				"""
						SELECT id
						FROM lunch_group_messages
						WHERE group_id = ?
						ORDER BY created_at, id
						""",
				(resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
				groupId
		);
	}

	private List<UUID> messageIdsAtTime(
			UUID groupId,
			LocalDateTime createdAt
	) {
		return jdbcTemplate.query(
				"""
						SELECT id
						FROM lunch_group_messages
						WHERE group_id = ?
						  AND created_at = ?
						ORDER BY created_at, id
						""",
				(resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
				groupId,
				createdAt
		);
	}

	private void closeGroupWhileHoldingLock(
			UUID groupId,
			CountDownLatch groupLocked,
			CountDownLatch allowClose
	) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			LunchGroup locked = groupRepository.findByIdForUpdate(groupId)
					.orElseThrow();
			groupLocked.countDown();
			awaitSignal(allowClose, "Concurrent close signal timed out");
			locked.setStatus(LunchGroupStatus.COMPLETED);
			locked.setCompletedAt(LocalDateTime.now());
		});
	}

	private LunchChatSendResult sendWhileHoldingGroupLock(
			UUID userId,
			CountDownLatch messageSaved,
			CountDownLatch allowCommit
	) {
		LunchChatSendResult result = new TransactionTemplate(transactionManager)
				.execute(status -> {
					LunchChatSendResult saved = chatService.send(
							userId,
							new CreateLunchChatMessageRequest(
									UUID.randomUUID(),
									"Committed before lifecycle"
							)
					);
					messageSaved.countDown();
					awaitSignal(allowCommit, "Concurrent send signal timed out");
					return saved;
				});
		if (result == null) {
			throw new IllegalStateException("Concurrent send returned no result");
		}
		return result;
	}

	private int sendStatus(AuthenticatedTestUser user, String body) throws Exception {
		return mockMvc.perform(user.authorize(post(MESSAGES_PATH))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andReturn()
				.getResponse()
				.getStatus();
	}

	private void awaitSignal(CountDownLatch signal, String timeoutMessage) {
		try {
			if (!signal.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException(timeoutMessage);
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(timeoutMessage, exception);
		}
	}

	private int sendAfterSignal(
			AuthenticatedTestUser user,
			String body,
			CountDownLatch ready,
			CountDownLatch start
	) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Chat send start signal timed out");
		}
		return mockMvc.perform(user.authorize(post(MESSAGES_PATH))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andReturn()
				.getResponse()
				.getStatus();
	}

	private String requestBody(UUID clientMessageId, String content) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
				"clientMessageId",
				clientMessageId,
				"content",
				content
		));
	}

	private UUID responseId(MvcResult result) throws Exception {
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(body.path("id").asText());
	}

	private Participant createParticipant(String displayName) throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser.TestProfile created = user.createProfile(
				displayName + " " + UUID.randomUUID()
		);
		Profile profile = profileRepository.findById(
				UUID.fromString(created.id())
		).orElseThrow();
		profile.setStatus(ProfileStatus.ACTIVE);
		return new Participant(user, profileRepository.saveAndFlush(profile));
	}

	private LunchGroup createGroup() {
		LunchGroup group = new LunchGroup();
		group.setLocation(location);
		group.setTimeSlot(LocalDateTime.of(2026, 8, 23, 14, 0));
		group.setTopic(LunchTopic.STUDY);
		return groupRepository.saveAndFlush(group);
	}

	private LunchGroupMember createMember(LunchGroup group, Profile profile) {
		LunchRequest request = new LunchRequest();
		request.setProfile(profile);
		request.setLocation(location);
		request.setStatus(LunchRequestStatus.MATCHED);
		request.setTopic(group.getTopic());
		request.setTimeSlot(group.getTimeSlot());
		request = requestRepository.saveAndFlush(request);

		LunchGroupMember member = new LunchGroupMember();
		member.setId(new LunchGroupMemberId(group.getId(), profile.getId()));
		member.setGroup(group);
		member.setProfile(profile);
		member.setLunchRequest(request);
		return memberRepository.saveAndFlush(member);
	}

	private Location createLocation() {
		University university = new University();
		university.setName("Chat API University " + UUID.randomUUID());
		university.setCity("Saint Petersburg");
		university = universityRepository.saveAndFlush(university);

		Location lunchLocation = new Location();
		lunchLocation.setUniversity(university);
		lunchLocation.setType(LocationType.DINING_ROOM);
		lunchLocation.setName("Chat API Canteen " + UUID.randomUUID());
		lunchLocation.setAddress("Kronverksky Prospekt 49");
		return locationRepository.saveAndFlush(lunchLocation);
	}

	private record Participant(AuthenticatedTestUser user, Profile profile) {
	}
}
