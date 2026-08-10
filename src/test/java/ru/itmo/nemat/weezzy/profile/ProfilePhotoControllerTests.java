package ru.itmo.nemat.weezzy.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.profile.photo.ProfilePhotoRepository;
import ru.itmo.nemat.weezzy.storage.ObjectStorageService;
import ru.itmo.nemat.weezzy.storage.dto.PresignedDownload;
import ru.itmo.nemat.weezzy.storage.dto.PresignedUpload;
import ru.itmo.nemat.weezzy.storage.dto.StoredObjectMetadata;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ProfilePhotoControllerTests {
	private static final String PHOTOS_URL = "/api/profiles/me/photos";
	private static final DockerImageName POSTGRES_IMAGE = DockerImageName
			.parse("pgvector/pgvector:pg17")
			.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>(POSTGRES_IMAGE)
					.withDatabaseName("weezzy")
					.withUsername("weezzy")
					.withPassword("weezzy_dev_password");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ProfilePhotoRepository photoRepository;

	@MockitoBean
	private ObjectStorageService storageService;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void configureStorage() {
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
		when(storageService.createUpload(anyString(), anyString(), anyLong()))
				.thenReturn(new PresignedUpload("http://storage/upload", expiresAt));
		when(storageService.createDownload(anyString()))
				.thenReturn(new PresignedDownload("http://storage/download", expiresAt));
		when(storageService.getMetadata(anyString()))
				.thenReturn(Optional.of(new StoredObjectMetadata("image/jpeg", 100)));
	}

	@Test
	void userCanManagePhotoLifecycle() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		user.createProfile("Photo Owner");
		String firstPhotoId = createUpload(user);
		String secondPhotoId = createUpload(user);

		confirm(user, firstPhotoId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.avatar").value(true));
		confirm(user, secondPhotoId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.avatar").value(false));

		mockMvc.perform(user.authorize(patch(PHOTOS_URL + "/order"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"photoIds":["%s","%s"]}
								""".formatted(secondPhotoId, firstPhotoId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(secondPhotoId))
				.andExpect(jsonPath("$[0].position").value(0));

		mockMvc.perform(user.authorize(put(
						PHOTOS_URL + "/" + secondPhotoId + "/avatar"
				)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.avatar").value(true));

		mockMvc.perform(user.authorize(delete(PHOTOS_URL + "/" + secondPhotoId)))
				.andExpect(status().isNoContent());
		mockMvc.perform(user.authorize(get(PHOTOS_URL)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(firstPhotoId))
				.andExpect(jsonPath("$[0].avatar").value(true))
				.andExpect(jsonPath("$[0].position").value(0));

		verify(storageService).deleteObject(anyString());
	}

	@Test
	void uploadRequestValidatesAuthenticationAndContentType() throws Exception {
		mockMvc.perform(post(PHOTOS_URL + "/uploads")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validUploadJson()))
				.andExpect(status().isUnauthorized());

		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		user.createProfile("Invalid Photo Owner");
		mockMvc.perform(user.authorize(post(PHOTOS_URL + "/uploads"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"contentType":"application/pdf","sizeBytes":100}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void uploadRequestEnforcesSizeAndPhotoCountLimits() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		user.createProfile("Limited Photo Owner");
		mockMvc.perform(user.authorize(post(PHOTOS_URL + "/uploads"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"contentType":"image/jpeg","sizeBytes":10485761}
								"""))
				.andExpect(status().isBadRequest());

		for (int photo = 0; photo < 6; photo++) {
			createUpload(user);
		}
		mockMvc.perform(user.authorize(post(PHOTOS_URL + "/uploads"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(validUploadJson()))
				.andExpect(status().isConflict());
	}

	@Test
	void userCannotConfirmAnotherProfilesPhoto() throws Exception {
		AuthenticatedTestUser owner = AuthenticatedTestUser.register(mockMvc, objectMapper);
		owner.createProfile("Photo Owner");
		String photoId = createUpload(owner);
		AuthenticatedTestUser other = AuthenticatedTestUser.register(mockMvc, objectMapper);
		other.createProfile("Other Photo Owner");

		confirm(other, photoId)
				.andExpect(status().isNotFound());
	}

	@Test
	void confirmRequiresUploadedObject() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		user.createProfile("Missing Upload Owner");
		String photoId = createUpload(user);
		when(storageService.getMetadata(anyString())).thenReturn(Optional.empty());

		confirm(user, photoId)
				.andExpect(status().isConflict());
	}

	@Test
	void pendingPhotoCannotBecomeAvatar() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		user.createProfile("Pending Avatar Owner");
		String photoId = createUpload(user);

		mockMvc.perform(user.authorize(put(
						PHOTOS_URL + "/" + photoId + "/avatar"
				)))
				.andExpect(status().isConflict());
	}

	@Test
	void reorderRejectsDuplicatesAndMissingPhotos() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		user.createProfile("Invalid Order Owner");
		String firstPhotoId = createUpload(user);
		String secondPhotoId = createUpload(user);
		confirm(user, firstPhotoId).andExpect(status().isOk());
		confirm(user, secondPhotoId).andExpect(status().isOk());

		mockMvc.perform(user.authorize(patch(PHOTOS_URL + "/order"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"photoIds":["%s","%s"]}
								""".formatted(firstPhotoId, firstPhotoId)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void confirmRejectsAndDeletesObjectWithMismatchedMetadata() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		user.createProfile("Mismatched Metadata Owner");
		String photoId = createUpload(user);
		when(storageService.getMetadata(anyString())).thenReturn(Optional.of(
				new StoredObjectMetadata("image/png", 100)
		));

		confirm(user, photoId)
				.andExpect(status().isBadRequest());
		verify(storageService).deleteObject(anyString());
	}

	@Test
	void newUploadRemovesExpiredPendingPhoto() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		user.createProfile("Expired Pending Owner");
		UUID expiredPhotoId = UUID.fromString(createUpload(user));
		String objectKey = photoRepository.findById(expiredPhotoId)
				.orElseThrow()
				.getObjectKey();
		jdbcTemplate.update(
				"UPDATE profile_photos SET created_at = ? WHERE id = ?",
				LocalDateTime.now().minusDays(2),
				expiredPhotoId
		);

		createUpload(user);

		assertThat(photoRepository.existsById(expiredPhotoId)).isFalse();
		verify(storageService).deleteObject(objectKey);
	}

	private String createUpload(AuthenticatedTestUser user) throws Exception {
		String response = mockMvc.perform(user.authorize(post(PHOTOS_URL + "/uploads"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(validUploadJson()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.uploadUrl").value("http://storage/upload"))
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode json = objectMapper.readTree(response);
		return json.path("photoId").asText();
	}

	private org.springframework.test.web.servlet.ResultActions confirm(
			AuthenticatedTestUser user,
			String photoId
	) throws Exception {
		return mockMvc.perform(user.authorize(post(
				PHOTOS_URL + "/" + photoId + "/confirm"
		)));
	}

	private String validUploadJson() {
		return """
				{"contentType":"image/jpeg","sizeBytes":100}
				""";
	}
}
