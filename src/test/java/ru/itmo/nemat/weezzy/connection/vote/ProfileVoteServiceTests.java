package ru.itmo.nemat.weezzy.connection.vote;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ProfileVoteServiceTests {

	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProfileVoteService service;

	@Autowired
	private ProfileVoteRepository repository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void voteCreatesNewVote() throws Exception {
		UUID sourceProfileId = idFromLocation(createProfile("Vote Source"));
		UUID targetProfileId = idFromLocation(createProfile("Vote Target"));

		ProfileVote vote = service.vote(sourceProfileId, targetProfileId, ProfileVoteAction.LIKE);

		assertThat(vote.getSourceProfileId()).isEqualTo(sourceProfileId);
		assertThat(vote.getTargetProfileId()).isEqualTo(targetProfileId);
		assertThat(vote.getAction()).isEqualTo(ProfileVoteAction.LIKE);
		assertThat(vote.getCreatedAt()).isNotNull();
		assertThat(vote.getUpdatedAt()).isNotNull();
	}

	@Test
	void voteUpdatesExistingVoteForSamePair() throws Exception {
		UUID sourceProfileId = idFromLocation(createProfile("Vote Update Source"));
		UUID targetProfileId = idFromLocation(createProfile("Vote Update Target"));
		service.vote(sourceProfileId, targetProfileId, ProfileVoteAction.LIKE);

		ProfileVote updatedVote = service.vote(sourceProfileId, targetProfileId, ProfileVoteAction.PASS);

		assertThat(updatedVote.getAction()).isEqualTo(ProfileVoteAction.PASS);
		assertThat(repository.findAll())
				.filteredOn(vote -> vote.getSourceProfileId().equals(sourceProfileId)
						&& vote.getTargetProfileId().equals(targetProfileId))
				.hasSize(1);
	}

	@Test
	void voteRejectsSelfVote() throws Exception {
		UUID profileId = idFromLocation(createProfile("Vote Self"));

		assertThatThrownBy(() -> service.vote(profileId, profileId, ProfileVoteAction.LIKE))
				.isInstanceOf(SelfVoteException.class)
				.hasMessage("Profile cannot vote for itself: " + profileId);
	}

	private String createProfile(String displayName) throws Exception {
		return mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "%s",
								  "bio": "Created for vote tests",
								  "telegram": "@vote_test",
								  "faculty": "FICT",
								  "studyProgram": "Software Engineering",
								  "course": 2
								}
								""".formatted(displayName)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");
	}

	private UUID idFromLocation(String location) {
		return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
	}
}
