package ru.itmo.nemat.weezzy.onboarding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.goal.Goal;
import ru.itmo.nemat.weezzy.goal.GoalService;
import ru.itmo.nemat.weezzy.goal.dto.CreateGoalRequest;
import ru.itmo.nemat.weezzy.interest.Interest;
import ru.itmo.nemat.weezzy.interest.InterestService;
import ru.itmo.nemat.weezzy.interest.dto.CreateInterestRequest;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.profile.dto.CreateProfileRequest;
import ru.itmo.nemat.weezzy.profile.dto.UpdateProfileRequest;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoalService;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterestService;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillService;
import ru.itmo.nemat.weezzy.skill.Skill;
import ru.itmo.nemat.weezzy.skill.SkillService;
import ru.itmo.nemat.weezzy.skill.dto.CreateSkillRequest;

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
import static ru.itmo.nemat.weezzy.support.ProfilePhotoTestData.insertReadyPhoto;

@Testcontainers
@SpringBootTest
class OnboardingConcurrencyTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private ProfileService profileService;

	@Autowired
	private ProfileSkillService profileSkillService;

	@Autowired
	private ProfileInterestService profileInterestService;

	@Autowired
	private ProfileGoalService profileGoalService;

	@Autowired
	private SkillService skillService;

	@Autowired
	private InterestService interestService;

	@Autowired
	private GoalService goalService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private TransactionTemplate transactionTemplate;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void setUpTransactionTemplate() {
		transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Test
	@Timeout(30)
	void removalWinsProfileLockAndConcurrentActivationCannotUseStaleProgress()
			throws Exception {
		OnboardingProfile onboardingProfile = createCompleteHiddenProfile();
		CountDownLatch profileLocked = new CountDownLatch(1);
		CountDownLatch releaseProfile = new CountDownLatch(1);
		CountDownLatch activationStarted = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<?> removalFuture = executor.submit(() -> transactionTemplate
					.executeWithoutResult(status -> {
						profileService.findByIdForUpdate(onboardingProfile.profileId());
						profileLocked.countDown();
						await(releaseProfile);
						profileSkillService.removeSkill(
								onboardingProfile.profileId(),
								onboardingProfile.skillId()
						);
					}));
			await(profileLocked);
			Future<?> activationFuture = executor.submit(() -> {
				activationStarted.countDown();
				return profileService.update(
						onboardingProfile.profileId(),
						statusUpdate(ProfileStatus.ACTIVE)
				);
			});
			await(activationStarted);
			assertStillWaiting(activationFuture);

			releaseProfile.countDown();
			getResult(removalFuture);
			assertThatThrownBy(() -> getResult(activationFuture))
					.isInstanceOf(ProfileActivationNotAllowedException.class);

			Profile profile = profileService.findById(onboardingProfile.profileId());
			assertThat(profile.getStatus()).isEqualTo(ProfileStatus.DRAFT);
		} finally {
			releaseProfile.countDown();
			executor.shutdownNow();
		}
	}

	private OnboardingProfile createCompleteHiddenProfile() {
		String suffix = UUID.randomUUID().toString();
		Profile profile = profileService.create(new CreateProfileRequest(
				"Onboarding Concurrency",
				"Complete profile",
				"@onboarding_concurrency",
				"FICT",
				"Software Engineering",
				2
		));
		Skill skill = skillService.create(new CreateSkillRequest(
				"Onboarding concurrency skill " + suffix,
				"Concurrency test"
		));
		Interest interest = interestService.create(new CreateInterestRequest(
				"Onboarding concurrency interest " + suffix,
				"Concurrency test"
		));
		Goal goal = goalService.create(new CreateGoalRequest(
				"ONBOARDING_CONCURRENCY_" + suffix.replace("-", "").toUpperCase(),
				"Onboarding concurrency goal " + suffix,
				"Concurrency test"
		));
		profileSkillService.addSkill(profile.getId(), skill.getId());
		profileInterestService.addInterest(profile.getId(), interest.getId());
		profileGoalService.addGoal(profile.getId(), goal.getId());
		insertReadyPhoto(jdbcTemplate, profile.getId());
		profileService.update(profile.getId(), statusUpdate(ProfileStatus.HIDDEN));

		return new OnboardingProfile(profile.getId(), skill.getId());
	}

	private UpdateProfileRequest statusUpdate(ProfileStatus status) {
		return new UpdateProfileRequest(
				null,
				null,
				null,
				null,
				null,
				null,
				status
		);
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

	private record OnboardingProfile(UUID profileId, UUID skillId) {
	}
}
