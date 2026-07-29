package ru.itmo.nemat.weezzy.connection.match;

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
class ProfileMatchControllerTests {

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
	void getMatchesReturnsEmptyListWhenCurrentProfileHasNoMatches() throws Exception {
		TestProfile profile = createProfile("Match Empty Profile");

		mockMvc.perform(profile.owner().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
	}

	@Test
	void getMatchesReturnsOnlyCurrentProfilesMatches() throws Exception {
		TestProfile current = createProfile("Match Current Profile");
		TestProfile matched = createProfile("Match Own Candidate");
		TestProfile outsiderFirst = createProfile("Match Outsider First");
		TestProfile outsiderSecond = createProfile("Match Outsider Second");
		createMatch(current, matched);
		createMatch(outsiderFirst, outsiderSecond);

		mockMvc.perform(current.owner().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].matchedProfile.id").value(matched.id()))
				.andExpect(jsonPath("$[0].matchedProfile.displayName")
						.value("Match Own Candidate"))
				.andExpect(jsonPath("$[0].createdAt").isNotEmpty())
				.andExpect(content().string(not(containsString(outsiderFirst.id()))))
				.andExpect(content().string(not(containsString(outsiderSecond.id()))));
	}

	@Test
	void getMatchesRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/matches"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getMatchesReturnsNotFoundWhenCurrentUserHasNoProfile() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(user.authorize(get("/api/matches")))
				.andExpect(status().isNotFound());
	}

	private TestProfile createProfile(String displayName) throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile(displayName);
	}

	private void createMatch(TestProfile first, TestProfile second) throws Exception {
		performLike(first, second);
		performLike(second, first);
	}

	private void performLike(TestProfile source, TestProfile target) throws Exception {
		mockMvc.perform(source.owner().authorize(post("/api/votes/" + target.id()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "action": "LIKE"
								}
								"""))
				.andExpect(status().isOk());
	}
}
