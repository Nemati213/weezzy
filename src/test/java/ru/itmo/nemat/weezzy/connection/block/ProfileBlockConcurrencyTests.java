package ru.itmo.nemat.weezzy.connection.block;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.connection.ProfilePairLockService;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchId;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchRepository;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchService;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVoteAction;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVoteRepository;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVoteService;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.dto.CreateProfileRequest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class ProfileBlockConcurrencyTests {

	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private ProfilePairLockService pairLockService;

	@Autowired
	private ProfileBlockService blockService;

	@Autowired
	private ProfileBlockRepository blockRepository;

	@Autowired
	private ProfileVoteService voteService;

	@Autowired
	private ProfileVoteRepository voteRepository;

	@Autowired
	private ProfileMatchRepository matchRepository;

	@Autowired
	private ProfileMatchService matchService;

	@Autowired
	private ProfileService profileService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUpTransactionTemplate() {
		transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Test
	@Timeout(30)
	void blockWinsPairLockAndPreventsConcurrentVote() throws Exception {
		UUID blockerProfileId = createProfile("Concurrency Block First");
		UUID blockedProfileId = createProfile("Concurrency Vote Second");
		CountDownLatch pairLocked = new CountDownLatch(1);
		CountDownLatch releasePair = new CountDownLatch(1);
		CountDownLatch voteStarted = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<?> blockFuture = executor.submit(() -> inTransactionWithPairLock(
					blockerProfileId,
					blockedProfileId,
					pairLocked,
					releasePair,
					() -> blockService.block(blockerProfileId, blockedProfileId)
			));
			await(pairLocked);
			Future<?> voteFuture = executor.submit(() -> {
				voteStarted.countDown();
				return voteService.vote(
						blockedProfileId,
						blockerProfileId,
						ProfileVoteAction.LIKE
				);
			});
			await(voteStarted);
			assertStillWaiting(voteFuture);

			releasePair.countDown();
			getResult(blockFuture);
			assertThatThrownBy(() -> getResult(voteFuture))
					.isInstanceOf(ProfileInteractionBlockedException.class);

			assertThat(blockRepository.existsBetween(
					blockerProfileId,
					blockedProfileId
			)).isTrue();
			assertThat(voteRepository.findBySourceProfileIdAndTargetProfileId(
					blockedProfileId,
					blockerProfileId
			)).isEmpty();
			assertThat(matchRepository.findById(normalizedMatchId(
					blockerProfileId,
					blockedProfileId
			))).isEmpty();
		} finally {
			releasePair.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	@Timeout(30)
	void voteWinsPairLockAndBlockRemovesCreatedMatchButPreservesVotes() throws Exception {
		UUID firstProfileId = createProfile("Concurrency Vote First");
		UUID secondProfileId = createProfile("Concurrency Vote Second");
		voteService.vote(firstProfileId, secondProfileId, ProfileVoteAction.LIKE);
		CountDownLatch pairLocked = new CountDownLatch(1);
		CountDownLatch releasePair = new CountDownLatch(1);
		CountDownLatch blockStarted = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<?> voteFuture = executor.submit(() -> inTransactionWithPairLock(
					firstProfileId,
					secondProfileId,
					pairLocked,
					releasePair,
					() -> voteService.vote(
							secondProfileId,
							firstProfileId,
							ProfileVoteAction.LIKE
					)
			));
			await(pairLocked);
			Future<?> blockFuture = executor.submit(() -> {
				blockStarted.countDown();
				return blockService.block(firstProfileId, secondProfileId);
			});
			await(blockStarted);
			assertStillWaiting(blockFuture);

			releasePair.countDown();
			getResult(voteFuture);
			getResult(blockFuture);

			assertThat(blockRepository.existsBetween(
					firstProfileId,
					secondProfileId
			)).isTrue();
			assertThat(voteRepository.findBySourceProfileIdAndTargetProfileId(
					firstProfileId,
					secondProfileId
			)).get().extracting(vote -> vote.getAction())
					.isEqualTo(ProfileVoteAction.LIKE);
			assertThat(voteRepository.findBySourceProfileIdAndTargetProfileId(
					secondProfileId,
					firstProfileId
			)).get().extracting(vote -> vote.getAction())
					.isEqualTo(ProfileVoteAction.LIKE);
			assertThat(matchRepository.findById(normalizedMatchId(
					firstProfileId,
					secondProfileId
			))).isEmpty();
		} finally {
			releasePair.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	@Timeout(30)
	void concurrentRepeatedBlockWaitsForPairAndRemainsIdempotent() throws Exception {
		UUID blockerProfileId = createProfile("Concurrency Repeat Blocker");
		UUID blockedProfileId = createProfile("Concurrency Repeat Blocked");
		CountDownLatch pairLocked = new CountDownLatch(1);
		CountDownLatch releasePair = new CountDownLatch(1);
		CountDownLatch secondBlockStarted = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<?> firstBlockFuture = executor.submit(() -> inTransactionWithPairLock(
					blockerProfileId,
					blockedProfileId,
					pairLocked,
					releasePair,
					() -> blockService.block(blockerProfileId, blockedProfileId)
			));
			await(pairLocked);
			Future<?> secondBlockFuture = executor.submit(() -> {
				secondBlockStarted.countDown();
				return blockService.block(blockerProfileId, blockedProfileId);
			});
			await(secondBlockStarted);
			assertStillWaiting(secondBlockFuture);

			releasePair.countDown();
			getResult(firstBlockFuture);
			getResult(secondBlockFuture);

			assertThat(blockRepository
					.findByBlockerProfileIdOrderByCreatedAtDesc(blockerProfileId))
					.filteredOn(block -> block.getBlockedProfileId().equals(
							blockedProfileId
					))
					.hasSize(1);
		} finally {
			releasePair.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	@Timeout(30)
	void unmatchWinsPairLockAndConcurrentLikeDoesNotRecreateMatch() throws Exception {
		UUID firstProfileId = createProfile("Concurrency Unmatch First");
		UUID secondProfileId = createProfile("Concurrency Unmatch Second");
		voteService.vote(firstProfileId, secondProfileId, ProfileVoteAction.LIKE);
		voteService.vote(secondProfileId, firstProfileId, ProfileVoteAction.LIKE);
		CountDownLatch pairLocked = new CountDownLatch(1);
		CountDownLatch releasePair = new CountDownLatch(1);
		CountDownLatch likeStarted = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<?> unmatchFuture = executor.submit(() -> inTransactionWithPairLock(
					firstProfileId,
					secondProfileId,
					pairLocked,
					releasePair,
					() -> matchService.unmatch(firstProfileId, secondProfileId)
			));
			await(pairLocked);
			Future<?> likeFuture = executor.submit(() -> {
				likeStarted.countDown();
				return voteService.vote(
						secondProfileId,
						firstProfileId,
						ProfileVoteAction.LIKE
				);
			});
			await(likeStarted);
			assertStillWaiting(likeFuture);

			releasePair.countDown();
			getResult(unmatchFuture);
			getResult(likeFuture);

			assertThat(voteRepository.findBySourceProfileIdAndTargetProfileId(
					firstProfileId,
					secondProfileId
			)).get().extracting(vote -> vote.getAction())
					.isEqualTo(ProfileVoteAction.PASS);
			assertThat(matchRepository.findById(normalizedMatchId(
					firstProfileId,
					secondProfileId
			))).isEmpty();
		} finally {
			releasePair.countDown();
			executor.shutdownNow();
		}
	}

	private void inTransactionWithPairLock(
			UUID firstProfileId,
			UUID secondProfileId,
			CountDownLatch pairLocked,
			CountDownLatch releasePair,
			Runnable operation
	) {
		transactionTemplate.executeWithoutResult(status -> {
			pairLockService.lock(firstProfileId, secondProfileId);
			pairLocked.countDown();
			await(releasePair);
			operation.run();
		});
	}

	private void assertStillWaiting(Future<?> future) {
		assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
				.isInstanceOf(TimeoutException.class);
	}

	private Object getResult(Future<?> future) throws Exception {
		try {
			return future.get(10, TimeUnit.SECONDS);
		} catch (ExecutionException exception) {
			if (exception.getCause() instanceof Exception cause) {
				throw cause;
			}
			throw exception;
		}
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out waiting for concurrent task");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting", exception);
		}
	}

	private UUID createProfile(String displayName) {
		return profileService.create(new CreateProfileRequest(
				displayName,
				"Created for block concurrency tests",
				"@block_concurrency_test",
				"FICT",
				"Software Engineering",
				2
		)).getId();
	}

	private ProfileMatchId normalizedMatchId(UUID firstProfileId, UUID secondProfileId) {
		if (firstProfileId.toString().compareTo(secondProfileId.toString()) < 0) {
			return new ProfileMatchId(firstProfileId, secondProfileId);
		}

		return new ProfileMatchId(secondProfileId, firstProfileId);
	}
}
