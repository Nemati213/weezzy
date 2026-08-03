package ru.itmo.nemat.weezzy.connection.block;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ProfileBlockControllerTests {

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

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void blockIsDirectionalAndReturnedOnlyToBlocker() throws Exception {
		TestProfile blocker = createProfile("Block Direction Blocker");
		TestProfile blocked = createProfile("Block Direction Blocked");

		performBlock(blocker, blocked.id())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blockedProfile.id").value(blocked.id()))
				.andExpect(jsonPath("$.blockedProfile.displayName")
						.value("Block Direction Blocked"))
				.andExpect(jsonPath("$.blockedProfile.telegram").doesNotExist())
				.andExpect(jsonPath("$.createdAt").isNotEmpty());

		mockMvc.perform(blocker.owner().authorize(get("/api/blocks")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].blockedProfile.id").value(blocked.id()));
		mockMvc.perform(blocked.owner().authorize(get("/api/blocks")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void repeatedBlockDoesNotCreateDuplicate() throws Exception {
		TestProfile blocker = createProfile("Block Repeat Blocker");
		TestProfile blocked = createProfile("Block Repeat Blocked");
		performBlock(blocker, blocked.id()).andExpect(status().isOk());

		performBlock(blocker, blocked.id()).andExpect(status().isOk());

		mockMvc.perform(blocker.owner().authorize(get("/api/blocks")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1));
	}

	@Test
	void unblockRemovesOnlyCurrentProfilesDirection() throws Exception {
		TestProfile first = createProfile("Block Mutual First");
		TestProfile second = createProfile("Block Mutual Second");
		performBlock(first, second.id()).andExpect(status().isOk());
		performBlock(second, first.id()).andExpect(status().isOk());

		mockMvc.perform(first.owner().authorize(delete("/api/blocks/" + second.id())))
				.andExpect(status().isNoContent());

		mockMvc.perform(first.owner().authorize(get("/api/blocks")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());
		mockMvc.perform(second.owner().authorize(get("/api/blocks")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].blockedProfile.id").value(first.id()));
		performVote(first, second.id(), "LIKE")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString(
						"Profiles cannot interact while a block exists"
				)));
	}

	@Test
	void blockDeletesExistingMatchButPreservesVotes() throws Exception {
		TestProfile first = createProfile("Block Match First");
		TestProfile second = createProfile("Block Match Second");
		performVote(first, second.id(), "LIKE").andExpect(status().isOk());
		performVote(second, first.id(), "LIKE").andExpect(status().isOk());

		performBlock(first, second.id()).andExpect(status().isOk());

		mockMvc.perform(first.owner().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());
		mockMvc.perform(second.owner().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());
		mockMvc.perform(first.owner().authorize(get("/api/votes")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].targetProfileId").value(second.id()))
				.andExpect(jsonPath("$.content[0].action").value("LIKE"));
		mockMvc.perform(second.owner().authorize(get("/api/votes")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].targetProfileId").value(first.id()))
				.andExpect(jsonPath("$.content[0].action").value("LIKE"));
	}

	@Test
	void blockPreventsVotesInBothDirections() throws Exception {
		TestProfile blocker = createProfile("Block Vote Blocker");
		TestProfile blocked = createProfile("Block Vote Blocked");
		performBlock(blocker, blocked.id()).andExpect(status().isOk());

		performVote(blocker, blocked.id(), "LIKE")
				.andExpect(status().isConflict());
		performVote(blocked, blocker.id(), "LIKE")
				.andExpect(status().isConflict());
	}

	@Test
	void blockRejectsSelfAndMissingTarget() throws Exception {
		TestProfile profile = createProfile("Block Invalid Profile");

		performBlock(profile, profile.id())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString(
						"Profile cannot block itself"
				)));
		performBlock(profile, "00000000-0000-0000-0000-000000000000")
				.andExpect(status().isNotFound());
	}

	@Test
	void blockEndpointsRequireAuthentication() throws Exception {
		String profileId = "00000000-0000-0000-0000-000000000000";

		mockMvc.perform(get("/api/blocks"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/blocks/" + profileId))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/blocks/" + profileId))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void blockReturnsNotFoundWhenCurrentUserHasNoProfile() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		TestProfile target = createProfile("Block Missing Source Target");

		mockMvc.perform(user.authorize(post("/api/blocks/" + target.id())))
				.andExpect(status().isNotFound());
	}

	private TestProfile createProfile(String displayName) throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile(displayName);
	}

	private org.springframework.test.web.servlet.ResultActions performBlock(
			TestProfile blocker,
			String blockedProfileId
	) throws Exception {
		return mockMvc.perform(blocker.owner().authorize(
				post("/api/blocks/" + blockedProfileId)
		));
	}

	private org.springframework.test.web.servlet.ResultActions performVote(
			TestProfile source,
			String targetProfileId,
			String action
	) throws Exception {
		return mockMvc.perform(source.owner().authorize(post("/api/votes/" + targetProfileId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "action": "%s"
						}
						""".formatted(action)));
	}
}
