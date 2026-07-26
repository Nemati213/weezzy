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

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void createVoteReturnsSavedVote() throws Exception {
		String sourceProfile = createProfile("Vote Controller Source");
		String targetProfile = createProfile("Vote Controller Target");

		mockMvc.perform(post(sourceProfile + "/votes/" + idFromLocation(targetProfile))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "action": "LIKE"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sourceProfileId").value(idFromLocation(sourceProfile)))
				.andExpect(jsonPath("$.targetProfileId").value(idFromLocation(targetProfile)))
				.andExpect(jsonPath("$.action").value("LIKE"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty());
	}

	@Test
	void createVoteRejectsMissingAction() throws Exception {
		String sourceProfile = createProfile("Vote Missing Action Source");
		String targetProfile = createProfile("Vote Missing Action Target");

		mockMvc.perform(post(sourceProfile + "/votes/" + idFromLocation(targetProfile))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("action")));
	}

	@Test
	void createVoteUpdatesExistingVoteForSamePair() throws Exception {
		String sourceProfile = createProfile("Vote Controller Update Source");
		String targetProfile = createProfile("Vote Controller Update Target");
		vote(sourceProfile, targetProfile, "LIKE");

		mockMvc.perform(post(sourceProfile + "/votes/" + idFromLocation(targetProfile))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "action": "PASS"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.action").value("PASS"));

		mockMvc.perform(get(sourceProfile + "/votes"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("PASS")))
				.andExpect(content().string(not(containsString("LIKE"))));
	}

	@Test
	void createVoteRejectsSelfVote() throws Exception {
		String profile = createProfile("Vote Controller Self");

		mockMvc.perform(post(profile + "/votes/" + idFromLocation(profile))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "action": "LIKE"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("Profile cannot vote for itself")));
	}

	@Test
	void createVoteReturnsNotFoundForMissingSourceProfile() throws Exception {
		String targetProfile = createProfile("Vote Missing Source Target");

		mockMvc.perform(post("/api/profiles/00000000-0000-0000-0000-000000000000/votes/"
						+ idFromLocation(targetProfile))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "action": "LIKE"
								}
								"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void createVoteReturnsNotFoundForMissingTargetProfile() throws Exception {
		String sourceProfile = createProfile("Vote Missing Target Source");

		mockMvc.perform(post(sourceProfile + "/votes/00000000-0000-0000-0000-000000000000")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "action": "LIKE"
								}
								"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void getVotesReturnsVotesBySourceProfile() throws Exception {
		String sourceProfile = createProfile("Vote List Source");
		String firstTargetProfile = createProfile("Vote List First Target");
		String secondTargetProfile = createProfile("Vote List Second Target");
		vote(sourceProfile, firstTargetProfile, "LIKE");
		vote(sourceProfile, secondTargetProfile, "PASS");

		mockMvc.perform(get(sourceProfile + "/votes"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(idFromLocation(firstTargetProfile))))
				.andExpect(content().string(containsString(idFromLocation(secondTargetProfile))))
				.andExpect(content().string(containsString("LIKE")))
				.andExpect(content().string(containsString("PASS")));
	}

	@Test
	void getVotesReturnsNotFoundForMissingSourceProfile() throws Exception {
		mockMvc.perform(get("/api/profiles/00000000-0000-0000-0000-000000000000/votes"))
				.andExpect(status().isNotFound());
	}

	@Test
	void reciprocalLikesCreateMatch() throws Exception {
		String firstProfile = createProfile("Vote Match First");
		String secondProfile = createProfile("Vote Match Second");

		vote(firstProfile, secondProfile, "LIKE");

		mockMvc.perform(get(firstProfile + "/matches"))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));

		vote(secondProfile, firstProfile, "LIKE");

		mockMvc.perform(get(firstProfile + "/matches"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].matchedProfile.id").value(idFromLocation(secondProfile)))
				.andExpect(jsonPath("$[0].matchedProfile.displayName").value("Vote Match Second"))
				.andExpect(jsonPath("$[0].createdAt").isNotEmpty());

		mockMvc.perform(get(secondProfile + "/matches"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].matchedProfile.id").value(idFromLocation(firstProfile)))
				.andExpect(jsonPath("$[0].matchedProfile.displayName").value("Vote Match First"));
	}

	@Test
	void passDoesNotCreateMatch() throws Exception {
		String firstProfile = createProfile("Vote Match Pass First");
		String secondProfile = createProfile("Vote Match Pass Second");

		vote(firstProfile, secondProfile, "PASS");
		vote(secondProfile, firstProfile, "LIKE");

		mockMvc.perform(get(firstProfile + "/matches"))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
	}

	@Test
	void repeatedReciprocalLikeDoesNotDuplicateMatch() throws Exception {
		String firstProfile = createProfile("Vote Match Repeat First");
		String secondProfile = createProfile("Vote Match Repeat Second");
		vote(firstProfile, secondProfile, "LIKE");
		vote(secondProfile, firstProfile, "LIKE");

		vote(secondProfile, firstProfile, "LIKE");

		mockMvc.perform(get(firstProfile + "/matches"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	private String createProfile(String displayName) throws Exception {
		return mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "%s",
								  "bio": "Created for profile-vote controller tests",
								  "telegram": "@profile_vote_controller_test",
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

	private void vote(String sourceProfileLocation, String targetProfileLocation, String action) throws Exception {
		mockMvc.perform(post(sourceProfileLocation + "/votes/" + idFromLocation(targetProfileLocation))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "action": "%s"
								}
								""".formatted(action)))
				.andExpect(status().isOk());
	}

	private String idFromLocation(String location) {
		return location.substring(location.lastIndexOf('/') + 1);
	}
}
