package ru.itmo.nemat.weezzy.connection.vote;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ProfileVoteControllerTests {

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
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void currentUserVotesWithoutSendingSourceProfileId() throws Exception {
		TestProfile source = createProfile("Vote Controller Source");
		TestProfile target = createProfile("Vote Controller Target");

		performVote(source, target.id(), "LIKE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sourceProfileId").value(source.id()))
				.andExpect(jsonPath("$.targetProfileId").value(target.id()))
				.andExpect(jsonPath("$.action").value("LIKE"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty());
	}

	@Test
	void createVoteRejectsMissingAction() throws Exception {
		TestProfile source = createProfile("Vote Missing Action Source");
		TestProfile target = createProfile("Vote Missing Action Target");

		mockMvc.perform(source.owner().authorize(post("/api/votes/" + target.id()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("action")));
	}

	@Test
	void repeatedVoteUpdatesExistingVoteForSamePair() throws Exception {
		TestProfile source = createProfile("Vote Update Source");
		TestProfile target = createProfile("Vote Update Target");
		performVote(source, target.id(), "LIKE").andExpect(status().isOk());

		performVote(source, target.id(), "PASS")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.action").value("PASS"));

		mockMvc.perform(source.owner().authorize(get("/api/votes")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("PASS")))
				.andExpect(content().string(not(containsString("LIKE"))));
	}

	@Test
	void changingLikeToPassDeletesExistingMatch() throws Exception {
		TestProfile first = createProfile("Vote Pass Existing Match First");
		TestProfile second = createProfile("Vote Pass Existing Match Second");
		performVote(first, second.id(), "LIKE").andExpect(status().isOk());
		performVote(second, first.id(), "LIKE").andExpect(status().isOk());

		performVote(first, second.id(), "PASS")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.action").value("PASS"));

		mockMvc.perform(first.owner().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
		mockMvc.perform(second.owner().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
	}

	@Test
	void createVoteRejectsSelfVote() throws Exception {
		TestProfile profile = createProfile("Vote Controller Self");

		performVote(profile, profile.id(), "LIKE")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("Profile cannot vote for itself")));
	}

	@Test
	void voteReturnsNotFoundWhenCurrentUserHasNoProfile() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		TestProfile target = createProfile("Vote Missing Source Target");

		mockMvc.perform(user.authorize(post("/api/votes/" + target.id()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(voteJson("LIKE")))
				.andExpect(status().isNotFound());
	}

	@Test
	void voteReturnsNotFoundForMissingTargetProfile() throws Exception {
		TestProfile source = createProfile("Vote Missing Target Source");

		performVote(source, "00000000-0000-0000-0000-000000000000", "LIKE")
				.andExpect(status().isNotFound());
	}

	@Test
	void getVotesReturnsOnlyCurrentUsersVotes() throws Exception {
		TestProfile source = createProfile("Vote List Source");
		TestProfile firstTarget = createProfile("Vote List First Target");
		TestProfile secondTarget = createProfile("Vote List Second Target");
		performVote(source, firstTarget.id(), "LIKE").andExpect(status().isOk());
		performVote(source, secondTarget.id(), "PASS").andExpect(status().isOk());

		mockMvc.perform(source.owner().authorize(get("/api/votes")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(firstTarget.id())))
				.andExpect(content().string(containsString(secondTarget.id())))
				.andExpect(content().string(containsString("LIKE")))
				.andExpect(content().string(containsString("PASS")));
	}

	@Test
	void voteEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/votes"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/votes/00000000-0000-0000-0000-000000000000")
						.contentType(MediaType.APPLICATION_JSON)
						.content(voteJson("LIKE")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void reciprocalLikesCreateMatchForBothUsers() throws Exception {
		TestProfile first = createProfile("Vote Match First");
		TestProfile second = createProfile("Vote Match Second");

		performVote(first, second.id(), "LIKE").andExpect(status().isOk());
		mockMvc.perform(first.owner().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));

		performVote(second, first.id(), "LIKE").andExpect(status().isOk());

		mockMvc.perform(first.owner().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].matchedProfile.id").value(second.id()))
				.andExpect(jsonPath("$[0].matchedProfile.displayName").value("Vote Match Second"));
		mockMvc.perform(second.owner().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].matchedProfile.id").value(first.id()));
	}

	@Test
	void passDoesNotCreateMatch() throws Exception {
		TestProfile first = createProfile("Vote Match Pass First");
		TestProfile second = createProfile("Vote Match Pass Second");

		performVote(first, second.id(), "PASS").andExpect(status().isOk());
		performVote(second, first.id(), "LIKE").andExpect(status().isOk());

		mockMvc.perform(first.owner().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
	}

	@Test
	void repeatedReciprocalLikeDoesNotDuplicateMatch() throws Exception {
		TestProfile first = createProfile("Vote Match Repeat First");
		TestProfile second = createProfile("Vote Match Repeat Second");
		performVote(first, second.id(), "LIKE").andExpect(status().isOk());
		performVote(second, first.id(), "LIKE").andExpect(status().isOk());
		performVote(second, first.id(), "LIKE").andExpect(status().isOk());

		mockMvc.perform(first.owner().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	private TestProfile createProfile(String displayName) throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile(displayName);
	}

	private org.springframework.test.web.servlet.ResultActions performVote(
			TestProfile source,
			String targetProfileId,
			String action
	) throws Exception {
		return mockMvc.perform(source.owner().authorize(post("/api/votes/" + targetProfileId))
				.contentType(MediaType.APPLICATION_JSON)
				.content(voteJson(action)));
	}

	private String voteJson(String action) {
		return """
				{
				  "action": "%s"
				}
				""".formatted(action);
	}
}
