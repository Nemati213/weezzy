package ru.itmo.nemat.weezzy.connection.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchService;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVoteAction;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVoteService;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.dto.CreateProfileRequest;
import ru.itmo.nemat.weezzy.recommendation.impression.ProfileRecommendationImpressionService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class ProfileInteractionEventTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private ProfileInteractionEventRepository eventRepository;

	@Autowired
	private ProfileVoteService voteService;

	@Autowired
	private ProfileMatchService matchService;

	@Autowired
	private ProfileBlockService blockService;

	@Autowired
	private ProfileRecommendationImpressionService impressionService;

	@Autowired
	private ProfileService profileService;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void lifecycleActionsAppendInteractionEventsWithoutDuplicateStateTransitions() {
		UUID first = createProfile("Event First");
		UUID second = createProfile("Event Second");

		voteService.vote(first, second, ProfileVoteAction.LIKE);
		voteService.vote(second, first, ProfileVoteAction.LIKE);
		matchService.unmatch(first, second);
		blockService.block(first, second);
		blockService.block(first, second);
		blockService.unblock(first, second);
		voteService.vote(first, second, ProfileVoteAction.PASS);
		impressionService.recordImpressions(first, List.of(second));

		List<ProfileInteractionEvent> events = eventRepository.findAll();
		assertThat(events)
				.extracting(ProfileInteractionEvent::getEventType)
				.containsExactlyInAnyOrder(
						ProfileInteractionEventType.LIKE,
						ProfileInteractionEventType.LIKE,
						ProfileInteractionEventType.MATCH,
						ProfileInteractionEventType.UNMATCH,
						ProfileInteractionEventType.BLOCK,
						ProfileInteractionEventType.UNBLOCK,
						ProfileInteractionEventType.PASS,
						ProfileInteractionEventType.RECOMMENDATION_IMPRESSION
				);
		assertThat(events)
				.filteredOn(event -> event.getEventType()
						== ProfileInteractionEventType.BLOCK)
				.hasSize(1);
		assertThat(events).allSatisfy(event -> {
			assertThat(event.getId()).isNotNull();
			assertThat(event.getOccurredAt()).isNotNull();
			assertThat(event.getSourceProfileId()).isIn(first, second);
			assertThat(event.getTargetProfileId()).isIn(first, second);
		});
	}

	private UUID createProfile(String displayName) {
		return profileService.create(new CreateProfileRequest(
				displayName,
				"Created for interaction event tests",
				"@interaction_event_test",
				"FICT",
				"Software Engineering",
				2
		)).getId();
	}
}
