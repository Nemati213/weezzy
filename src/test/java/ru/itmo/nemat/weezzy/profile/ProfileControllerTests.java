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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ProfileControllerTests {

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
	void createProfileReturnsCreatedProfile() throws Exception {
		mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "Nemat",
								  "bio": "Backend developer at ITMO",
								  "telegram": "@nemati213",
								  "faculty": "FICT",
								  "studyProgram": "Software Engineering",
								  "course": 2
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", matchesPattern("/api/profiles/.+")))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.displayName").value("Nemat"))
				.andExpect(jsonPath("$.bio").value("Backend developer at ITMO"))
				.andExpect(jsonPath("$.telegram").value("@nemati213"))
				.andExpect(jsonPath("$.faculty").value("FICT"))
				.andExpect(jsonPath("$.studyProgram").value("Software Engineering"))
				.andExpect(jsonPath("$.course").value(2));
	}

	@Test
	void createProfileRejectsBlankDisplayName() throws Exception {
		mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "",
								  "bio": "Backend developer at ITMO",
								  "telegram": "@nemati213",
								  "faculty": "FICT",
								  "studyProgram": "Software Engineering",
								  "course": 2
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createProfileRejectsInvalidCourse() throws Exception {
		mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "Nemat",
								  "bio": "Backend developer at ITMO",
								  "telegram": "@nemati213",
								  "faculty": "FICT",
								  "studyProgram": "Software Engineering",
								  "course": 7
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getProfileReturnsExistingProfile() throws Exception {
		String location = mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "Profile To Fetch",
								  "bio": "Created for GET test",
								  "telegram": "@fetch_profile",
								  "faculty": "Faculty of Infocommunication Technologies",
								  "studyProgram": "Applied Computer Science",
								  "course": 3
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");

		mockMvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Profile To Fetch"))
				.andExpect(jsonPath("$.bio").value("Created for GET test"))
				.andExpect(jsonPath("$.telegram").value("@fetch_profile"))
				.andExpect(jsonPath("$.faculty").value("Faculty of Infocommunication Technologies"))
				.andExpect(jsonPath("$.studyProgram").value("Applied Computer Science"))
				.andExpect(jsonPath("$.course").value(3));
	}

	@Test
	void getProfileReturnsNotFoundForMissingProfile() throws Exception {
		mockMvc.perform(get("/api/profiles/00000000-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("Profile not found: 00000000-0000-0000-0000-000000000000"))
				.andExpect(jsonPath("$.path").value("/api/profiles/00000000-0000-0000-0000-000000000000"));
	}

	@Test
	void getAllProfilesReturnsCreatedProfiles() throws Exception {
		mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "Profile In List",
								  "bio": "Created for list test",
								  "telegram": "@list_profile",
								  "faculty": "FICT",
								  "studyProgram": "Software Engineering",
								  "course": 1
								}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/profiles"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Profile In List")))
				.andExpect(content().string(containsString("Software Engineering")));
	}

	@Test
	void updateProfileChangesOnlyProvidedFields() throws Exception {
		String location = mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "Before Update",
								  "bio": "Old bio",
								  "telegram": "@old_profile",
								  "faculty": "Old Faculty",
								  "studyProgram": "Old Program",
								  "course": 1
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");

		mockMvc.perform(patch(location)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bio": "New bio",
								  "course": 4
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Before Update"))
				.andExpect(jsonPath("$.bio").value("New bio"))
				.andExpect(jsonPath("$.telegram").value("@old_profile"))
				.andExpect(jsonPath("$.faculty").value("Old Faculty"))
				.andExpect(jsonPath("$.studyProgram").value("Old Program"))
				.andExpect(jsonPath("$.course").value(4));
	}

	@Test
	void updateProfileRejectsBlankDisplayName() throws Exception {
		String location = mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "Valid Name",
								  "bio": "Bio",
								  "telegram": "@valid_profile",
								  "faculty": "FICT",
								  "studyProgram": "Software Engineering",
								  "course": 2
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");

		mockMvc.perform(patch(location)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": ""
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateProfileRejectsInvalidCourse() throws Exception {
		String location = mockMvc.perform(post("/api/profiles")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "Valid Name",
								  "bio": "Bio",
								  "telegram": "@valid_profile",
								  "faculty": "FICT",
								  "studyProgram": "Software Engineering",
								  "course": 2
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader("Location");

		mockMvc.perform(patch(location)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "course": 0
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateProfileReturnsNotFoundForMissingProfile() throws Exception {
		mockMvc.perform(patch("/api/profiles/00000000-0000-0000-0000-000000000000")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "bio": "No one is here"
								}
								"""))
				.andExpect(status().isNotFound());
	}
}
