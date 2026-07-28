package ru.itmo.nemat.weezzy.support;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.itmo.nemat.weezzy.security.JwtService;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserRepository;
import ru.itmo.nemat.weezzy.user.UserRole;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public record AuthenticatedTestUser(MockMvc mockMvc, String token, String userId) {

	public static AuthenticatedTestUser register(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
		String email = "test-" + UUID.randomUUID() + "@itmo.ru";
		String response = mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "%s",
								  "password": "password123"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode json = objectMapper.readTree(response);

		return new AuthenticatedTestUser(
				mockMvc,
				json.path("accessToken").asText(),
				json.path("user").path("id").asText()
		);
	}

	public static AuthenticatedTestUser registerAdmin(
			MockMvc mockMvc,
			ObjectMapper objectMapper,
			UserRepository userRepository,
			JwtService jwtService
	) throws Exception {
		AuthenticatedTestUser registeredUser = register(mockMvc, objectMapper);
		User user = userRepository.findById(UUID.fromString(registeredUser.userId()))
				.orElseThrow();
		user.setRole(UserRole.ADMIN);
		userRepository.save(user);

		return new AuthenticatedTestUser(
				mockMvc,
				jwtService.generateAccessToken(user),
				registeredUser.userId()
		);
	}

	public TestProfile createProfile(String displayName) throws Exception {
		String location = mockMvc.perform(authorize(post("/api/profiles"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "displayName": "%s",
								  "bio": "Created by an authenticated test user",
								  "telegram": "@authenticated_test",
								  "faculty": "FICT",
								  "studyProgram": "Software Engineering",
								  "course": 2
								}
								""".formatted(displayName)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getHeader(HttpHeaders.LOCATION);

		if (location == null) {
			throw new IllegalStateException("Created profile response has no Location header");
		}

		return new TestProfile(this, location, idFromLocation(location));
	}

	public MockHttpServletRequestBuilder authorize(MockHttpServletRequestBuilder request) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private static String idFromLocation(String location) {
		return location.substring(location.lastIndexOf('/') + 1);
	}

	public record TestProfile(AuthenticatedTestUser owner, String location, String id) {
	}
}
