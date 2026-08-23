package ru.itmo.nemat.weezzy.lunch.chat;

import jakarta.persistence.EntityManager;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import ru.itmo.nemat.weezzy.lunch.chat.cleanup.LunchChatCleanupService;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestStatus;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false",
		"app.lunch.lifecycle.enabled=false"
})
class LunchChatMessageRepositoryTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private UniversityRepository universityRepository;

	@Autowired
	private LocationRepository locationRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private LunchRequestRepository lunchRequestRepository;

	@Autowired
	private LunchGroupRepository groupRepository;

	@Autowired
	private LunchGroupMemberRepository memberRepository;

	@Autowired
	private LunchChatMessageRepository messageRepository;

	@Autowired
	private LunchChatCleanupService cleanupService;

	@Autowired
	private MeterRegistry meterRegistry;

	@Autowired
	private EntityManager entityManager;

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
	void messageCanBeSavedForGroupMember() {
		LunchGroup group = createGroup();
		Profile sender = createMember(group, "Sender");
		UUID clientMessageId = UUID.randomUUID();

		LunchChatMessage saved = messageRepository.saveAndFlush(message(
				group,
				sender,
				clientMessageId,
				"See you near the entrance"
		));
		entityManager.clear();

		LunchChatMessage found = messageRepository
				.findBySenderProfileIdAndClientMessageId(
						sender.getId(),
						clientMessageId
				)
				.orElseThrow();

		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getGroup().getId()).isEqualTo(group.getId());
		assertThat(found.getSenderProfile().getId()).isEqualTo(sender.getId());
		assertThat(found.getClientMessageId()).isEqualTo(clientMessageId);
		assertThat(found.getContent()).isEqualTo("See you near the entrance");
		assertThat(found.getCreatedAt()).isNotNull();
	}

	@Test
	void duplicateClientMessageIdForSameSenderIsRejected() {
		LunchGroup group = createGroup();
		Profile sender = createMember(group, "Sender");
		UUID clientMessageId = UUID.randomUUID();
		messageRepository.saveAndFlush(message(
				group,
				sender,
				clientMessageId,
				"First attempt"
		));

		assertThatThrownBy(() -> messageRepository.saveAndFlush(message(
				group,
				sender,
				clientMessageId,
				"Retried attempt"
		))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sameClientMessageIdCanBeUsedByDifferentSenders() {
		LunchGroup group = createGroup();
		Profile firstSender = createMember(group, "First");
		Profile secondSender = createMember(group, "Second");
		UUID clientMessageId = UUID.randomUUID();

		messageRepository.saveAndFlush(message(
				group,
				firstSender,
				clientMessageId,
				"First sender"
		));
		messageRepository.saveAndFlush(message(
				group,
				secondSender,
				clientMessageId,
				"Second sender"
		));

		assertThat(messageRepository.findBySenderProfileIdAndClientMessageId(
				firstSender.getId(),
				clientMessageId
		)).isPresent();
		assertThat(messageRepository.findBySenderProfileIdAndClientMessageId(
				secondSender.getId(),
				clientMessageId
		)).isPresent();
	}

	@Test
	void messageFromProfileOutsideGroupIsRejected() {
		LunchGroup group = createGroup();
		Profile outsider = createProfile("Outsider");

		assertThatThrownBy(() -> messageRepository.saveAndFlush(message(
				group,
				outsider,
				UUID.randomUUID(),
				"I should not be here"
		))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void blankContentIsRejected() {
		LunchGroup group = createGroup();
		Profile sender = createMember(group, "Sender");

		assertThatThrownBy(() -> messageRepository.saveAndFlush(message(
				group,
				sender,
				UUID.randomUUID(),
				" \t "
		))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void contentLongerThanLimitIsRejected() {
		LunchGroup group = createGroup();
		Profile sender = createMember(group, "Sender");

		assertThatThrownBy(() -> messageRepository.saveAndFlush(message(
				group,
				sender,
				UUID.randomUUID(),
				"a".repeat(LunchChatMessage.MAX_CONTENT_LENGTH + 1)
		))).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void cleanupUsesGroupEndTimeAndKeepsActiveOrRecentMessages() {
		LocalDateTime now = LocalDateTime.of(2026, 8, 30, 12, 0);
		LunchGroup oldCompleted = createGroup();
		UUID oldCompletedMessage = saveMessage(
				oldCompleted,
				createMember(oldCompleted, "Old completed")
		).getId();
		complete(oldCompleted, now.minusDays(8));

		LunchGroup boundaryCancelled = createGroup();
		UUID boundaryCancelledMessage = saveMessage(
				boundaryCancelled,
				createMember(boundaryCancelled, "Boundary cancelled")
		).getId();
		cancel(boundaryCancelled, now.minusDays(7));

		LunchGroup recentCompleted = createGroup();
		UUID recentMessage = saveMessage(
				recentCompleted,
				createMember(recentCompleted, "Recent completed")
		).getId();
		complete(recentCompleted, now.minusDays(7).plusSeconds(1));

		LunchGroup active = createGroup();
		UUID activeMessage = saveMessage(
				active,
				createMember(active, "Active")
		).getId();
		active.setCompletedAt(now.minusDays(30));
		groupRepository.saveAndFlush(active);

		double successfulRunsBefore = counter(
				"weezzy.lunch.chat.cleanup.runs",
				"outcome",
				"success"
		);
		double deletedBefore = counter(
				"weezzy.lunch.chat.cleanup.messages.deleted",
				null,
				null
		);
		long durationCountBefore = meterRegistry
				.get("weezzy.lunch.chat.cleanup.duration")
				.timer()
				.count();

		int deleted = cleanupService.deleteExpired(now, 100);

		assertThat(deleted).isEqualTo(2);
		assertThat(messageRepository.existsById(oldCompletedMessage)).isFalse();
		assertThat(messageRepository.existsById(boundaryCancelledMessage)).isFalse();
		assertThat(messageRepository.existsById(recentMessage)).isTrue();
		assertThat(messageRepository.existsById(activeMessage)).isTrue();
		assertThat(counter(
				"weezzy.lunch.chat.cleanup.runs",
				"outcome",
				"success"
		)).isEqualTo(successfulRunsBefore + 1);
		assertThat(counter(
				"weezzy.lunch.chat.cleanup.messages.deleted",
				null,
				null
		)).isEqualTo(deletedBefore + 2);
		assertThat(meterRegistry.get("weezzy.lunch.chat.cleanup.duration")
				.timer()
				.count()).isEqualTo(durationCountBefore + 1);
	}

	@Test
	void cleanupRespectsBatchSize() {
		LocalDateTime now = LocalDateTime.of(2026, 8, 30, 12, 0);
		LunchGroup group = createGroup();
		Profile sender = createMember(group, "Batch sender");
		for (int index = 0; index < 3; index++) {
			saveMessage(group, sender);
		}
		complete(group, now.minusDays(8));

		assertThat(cleanupService.deleteExpired(now, 2)).isEqualTo(2);
		assertThat(messageRepository.countByGroupIdAndSenderProfileId(
				group.getId(),
				sender.getId()
		)).isEqualTo(1);
		assertThat(cleanupService.deleteExpired(now, 2)).isEqualTo(1);
		assertThat(messageRepository.countByGroupIdAndSenderProfileId(
				group.getId(),
				sender.getId()
		)).isZero();
	}

	@Test
	void concurrentCleanupWorkersDeleteEachMessageExactlyOnce() throws Exception {
		LocalDateTime now = LocalDateTime.of(2026, 8, 30, 12, 0);
		LunchGroup group = createGroup();
		Profile sender = createMember(group, "Concurrent cleanup sender");
		List<UUID> messageIds = new ArrayList<>();
		for (int index = 0; index < 20; index++) {
			messageIds.add(saveMessage(group, sender).getId());
		}
		complete(group, now.minusDays(8));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<Integer> first = executor.submit(() -> cleanupAfterSignal(
					now,
					ready,
					start
			));
			Future<Integer> second = executor.submit(() -> cleanupAfterSignal(
					now,
					ready,
					start
			));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(first.get(30, TimeUnit.SECONDS)
					+ second.get(30, TimeUnit.SECONDS)).isEqualTo(messageIds.size());
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(messageRepository.findAllById(messageIds)).isEmpty();
	}

	private int cleanupAfterSignal(
			LocalDateTime now,
			CountDownLatch ready,
			CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Cleanup start signal timed out");
		}
		return cleanupService.deleteExpired(now, 20);
	}

	private LunchChatMessage saveMessage(LunchGroup group, Profile sender) {
		return messageRepository.saveAndFlush(message(
				group,
				sender,
				UUID.randomUUID(),
				"Cleanup message " + UUID.randomUUID()
		));
	}

	private void complete(LunchGroup group, LocalDateTime completedAt) {
		group.setStatus(LunchGroupStatus.COMPLETED);
		group.setCompletedAt(completedAt);
		groupRepository.saveAndFlush(group);
	}

	private void cancel(LunchGroup group, LocalDateTime cancelledAt) {
		group.setStatus(LunchGroupStatus.CANCELLED);
		group.setCancelledAt(cancelledAt);
		groupRepository.saveAndFlush(group);
	}

	private double counter(String name, String tagName, String tagValue) {
		var search = meterRegistry.get(name);
		if (tagName != null) {
			search = search.tag(tagName, tagValue);
		}
		return search.counter().count();
	}

	private LunchChatMessage message(
			LunchGroup group,
			Profile sender,
			UUID clientMessageId,
			String content
	) {
		LunchChatMessage message = new LunchChatMessage();
		message.setGroup(group);
		message.setSenderProfile(sender);
		message.setClientMessageId(clientMessageId);
		message.setContent(content);
		return message;
	}

	private LunchGroup createGroup() {
		LunchGroup group = new LunchGroup();
		group.setLocation(location);
		group.setTimeSlot(slot());
		group.setTopic(LunchTopic.STUDY);
		group.setStatus(LunchGroupStatus.ACTIVE);
		return groupRepository.saveAndFlush(group);
	}

	private Profile createMember(LunchGroup group, String displayName) {
		Profile profile = createProfile(displayName);
		LunchRequest request = createRequest(profile);

		LunchGroupMember member = new LunchGroupMember();
		member.setId(new LunchGroupMemberId(group.getId(), profile.getId()));
		member.setGroup(group);
		member.setProfile(profile);
		member.setLunchRequest(request);
		memberRepository.saveAndFlush(member);
		return profile;
	}

	private Profile createProfile(String displayName) {
		Profile profile = new Profile();
		profile.setDisplayName(displayName + " " + UUID.randomUUID());
		profile.setStatus(ProfileStatus.ACTIVE);
		return profileRepository.saveAndFlush(profile);
	}

	private LunchRequest createRequest(Profile profile) {
		LunchRequest request = new LunchRequest();
		request.setProfile(profile);
		request.setLocation(location);
		request.setStatus(LunchRequestStatus.MATCHED);
		request.setTopic(LunchTopic.STUDY);
		request.setTimeSlot(slot());
		return lunchRequestRepository.saveAndFlush(request);
	}

	private Location createLocation() {
		University university = new University();
		university.setName("Chat Test University " + UUID.randomUUID());
		university.setCity("Saint Petersburg");
		university = universityRepository.saveAndFlush(university);

		Location lunchLocation = new Location();
		lunchLocation.setUniversity(university);
		lunchLocation.setType(LocationType.DINING_ROOM);
		lunchLocation.setName("Chat Test Canteen " + UUID.randomUUID());
		lunchLocation.setAddress("Kronverksky Prospekt 49");
		return locationRepository.saveAndFlush(lunchLocation);
	}

	private LocalDateTime slot() {
		return LocalDateTime.of(2026, 8, 23, 12, 30);
	}
}
