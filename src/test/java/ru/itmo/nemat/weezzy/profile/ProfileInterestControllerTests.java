package ru.itmo.nemat.weezzy.profile;

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
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ProfileInterestControllerTests {

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
	void addInterestToProfileReturnsLinkedInterest() throws Exception {
		String profile = createProfile("Interest Link Profile");
		String interest = createInterest("Profile Interest Startups");

		mockMvc.perform(post(profile + "/interests/" + idFromLocation(interest)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern(profile + "/interests/.+")))
				.andExpect(jsonPath("$.name").value("Profile Interest Startups"));
	}

	@Test
	void getProfileInterestsReturnsLinkedInterests() throws Exception {
		String profile = createProfile("Interest List Profile");
		String interest = createInterest("Profile Interest Hackathons");
		addInterest(profile, interest);

		mockMvc.perform(get(profile + "/interests"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Profile Interest Hackathons")));
	}

	@Test
	void addInterestToProfileRejectsDuplicateLink() throws Exception {
		String profile = createProfile("Interest Duplicate Profile");
		String interest = createInterest("Profile Interest Duplicate");
		String linkUrl = profile + "/interests/" + idFromLocation(interest);
		addInterest(profile, interest);

		mockMvc.perform(post(linkUrl))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value(containsString("Profile already has interest")));
	}

	@Test
	void removeInterestFromProfileDeletesLink() throws Exception {
		String profile = createProfile("Interest Delete Profile");
		String interest = createInterest("Profile Interest Delete");
		String linkUrl = profile + "/interests/" + idFromLocation(interest);
		addInterest(profile, interest);

		mockMvc.perform(delete(linkUrl))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(profile + "/interests"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Profile Interest Delete"))));
	}

	@Test
	void addInterestToMissingProfileReturnsNotFound() throws Exception {
		String interest = createInterest("Profile Interest Missing Profile");

		mockMvc.perform(post("/api/profiles/00000000-0000-0000-0000-000000000000/interests/"
						+ idFromLocation(interest)))
				.andExpect(status().isNotFound());
	}

	@Test
	void addMissingInterestToProfileReturnsNotFound() throws Exception {
		String profile = createProfile("Missing Interest Profile");

		mockMvc.perform(post(profile + "/interests/00000000-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound());
	}

	@Test
	void removeMissingProfileInterestLinkReturnsNotFound() throws Exception {
		String profile = createProfile("Missing Interest Link Profile");
		String interest = createInterest("Profile Interest Missing Link");

		mockMvc.perform(delete(profile + "/interests/" + idFromLocation(interest)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(containsString("Profile interest link not found")));
	}

	private String createProfile(String displayName) throws Exception {
		return mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "%s",
								  "bio": "Created for profile-interest tests",
								  "telegram": "@profile_interest_test",
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

	private String createInterest(String name) throws Exception {
		return mockMvc.perform(post("/api/interests")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s",
								  "description": "Created for profile-interest tests"
								}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");
	}

	private void addInterest(String profileLocation, String interestLocation) throws Exception {
		mockMvc.perform(post(profileLocation + "/interests/" + idFromLocation(interestLocation)))
				.andExpect(status().isCreated());
	}

	private String idFromLocation(String location) {
		return location.substring(location.lastIndexOf('/') + 1);
	}
}
