package ru.itmo.nemat.weezzy.connection.vote;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.dto.CreateProfileRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@Testcontainers
@SpringBootTest
class ProfileVoteServiceTests {

	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private ProfileVoteService service;

	@Autowired
	private ProfileVoteRepository repository;

	@Autowired
	private ProfileService profileService;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void voteCreatesNewVote() {
		UUID sourceProfileId = createProfile("Vote Source");
		UUID targetProfileId = createProfile("Vote Target");

		ProfileVote vote = service.vote(sourceProfileId, targetProfileId, ProfileVoteAction.LIKE);

		assertThat(vote.getSourceProfileId()).isEqualTo(sourceProfileId);
		assertThat(vote.getTargetProfileId()).isEqualTo(targetProfileId);
		assertThat(vote.getAction()).isEqualTo(ProfileVoteAction.LIKE);
		assertThat(vote.getCreatedAt()).isNotNull();
		assertThat(vote.getUpdatedAt()).isNotNull();
	}

	@Test
	void voteUpdatesExistingVoteForSamePair() {
		UUID sourceProfileId = createProfile("Vote Update Source");
		UUID targetProfileId = createProfile("Vote Update Target");
		service.vote(sourceProfileId, targetProfileId, ProfileVoteAction.LIKE);

		ProfileVote updatedVote = service.vote(sourceProfileId, targetProfileId, ProfileVoteAction.PASS);

		assertThat(updatedVote.getAction()).isEqualTo(ProfileVoteAction.PASS);
		assertThat(repository.findAll())
				.filteredOn(vote -> vote.getSourceProfileId().equals(sourceProfileId)
						&& vote.getTargetProfileId().equals(targetProfileId))
				.hasSize(1);
	}

	@Test
	void voteRejectsSelfVote() {
		UUID profileId = createProfile("Vote Self");

		assertThatThrownBy(() -> service.vote(profileId, profileId, ProfileVoteAction.LIKE))
				.isInstanceOf(SelfVoteException.class)
				.hasMessage("Profile cannot vote for itself: " + profileId);
	}

	private UUID createProfile(String displayName) {
		return profileService.create(new CreateProfileRequest(
				displayName,
				"Created for vote tests",
				"@vote_test",
				"FICT",
				"Software Engineering",
				2
		)).getId();
	}
}
