package ru.itmo.nemat.weezzy.lunch.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.location.Location;
import ru.itmo.nemat.weezzy.location.LocationRepository;
import ru.itmo.nemat.weezzy.location.LocationType;
import ru.itmo.nemat.weezzy.location.University;
import ru.itmo.nemat.weezzy.location.UniversityRepository;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(LunchRequestControllerTests.TestClockConfiguration.class)
class LunchRequestControllerTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");
	private static final ZoneId LUNCH_ZONE = ZoneId.of("Europe/Moscow");
	private static final Instant DEFAULT_INSTANT = LocalDateTime
			.of(2026, 8, 14, 12, 3)
			.atZone(LUNCH_ZONE)
			.toInstant();

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private UniversityRepository universityRepository;

	@Autowired
	private LocationRepository locationRepository;

	@Autowired
	private LunchRequestRepository lunchRequestRepository;

	@Autowired
	private MutableClock clock;

	private AuthenticatedTestUser user;
	private Profile profile;
	private Location location;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void setUp() throws Exception {
		clock.setInstant(DEFAULT_INSTANT);
		user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser.TestProfile testProfile = user.createProfile(
				"Lunch user " + UUID.randomUUID()
		);
		profile = profileRepository.findById(UUID.fromString(testProfile.id()))
				.orElseThrow();
		profile.setStatus(ProfileStatus.ACTIVE);
		profile = profileRepository.saveAndFlush(profile);
		location = createLocation();
	}

	@Test
	void createRoundsTimeSlotAndReturnsSearchingRequest() throws Exception {
		JsonNode response = createRequest("NOW", "STUDY", "  Java and lunch  ");

		mockMvc.perform(user.authorize(get("/api/lunch/requests/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(response.path("id").asText()))
				.andExpect(jsonPath("$.profileId").value(profile.getId().toString()))
				.andExpect(jsonPath("$.location.id").value(location.getId().toString()))
				.andExpect(jsonPath("$.status").value("SEARCHING"))
				.andExpect(jsonPath("$.topic").value("STUDY"))
				.andExpect(jsonPath("$.comment").value("Java and lunch"))
				.andExpect(jsonPath("$.timeSlot").value("2026-08-14T12:15:00"))
				.andExpect(jsonPath("$.extensionCount").value(0));
	}

	@Test
	void lunchRequestEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(post("/api/lunch/requests")
						.contentType(MediaType.APPLICATION_JSON)
						.content(lunchJson("NOW", "STUDY", null)))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/lunch/requests/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void createRejectsInactiveProfile() throws Exception {
		profile.setStatus(ProfileStatus.DRAFT);
		profileRepository.saveAndFlush(profile);

		mockMvc.perform(user.authorize(post("/api/lunch/requests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(lunchJson("NOW", "STUDY", null)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", containsString("Profile must be active")));
	}

	@Test
	void createRejectsSecondActiveRequest() throws Exception {
		createRequest("NOW", "STUDY", null);

		mockMvc.perform(user.authorize(post("/api/lunch/requests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(lunchJson("IN_30_MINUTES", "NETWORKING", null)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath(
						"$.message",
						containsString("Active lunch request already exists")
				));
	}

	@Test
	void createRejectsProfileAlreadyMatchedToday() throws Exception {
		LunchRequest matched = findRequest(createRequest("NOW", "STUDY", null));
		matched.setStatus(LunchRequestStatus.MATCHED);
		lunchRequestRepository.saveAndFlush(matched);

		mockMvc.perform(user.authorize(post("/api/lunch/requests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(lunchJson("IN_30_MINUTES", "NETWORKING", null)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", containsString("already has a lunch match")));
	}

	@Test
	void matchedRequestFromPreviousDayDoesNotBlockCreation() throws Exception {
		LunchRequest oldMatch = findRequest(createRequest("NOW", "STUDY", null));
		oldMatch.setStatus(LunchRequestStatus.MATCHED);
		oldMatch.setTimeSlot(LocalDateTime.of(2026, 8, 13, 12, 15));
		lunchRequestRepository.saveAndFlush(oldMatch);

		mockMvc.perform(user.authorize(post("/api/lunch/requests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(lunchJson("IN_30_MINUTES", "NETWORKING", null)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SEARCHING"))
				.andExpect(jsonPath("$.timeSlot").value("2026-08-14T12:45:00"));
	}

	@Test
	void createRejectsRequestOutsideDailyWindow() throws Exception {
		clock.setLocalDateTime(LocalDateTime.of(2026, 8, 14, 11, 59));

		mockMvc.perform(user.authorize(post("/api/lunch/requests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(lunchJson("NOW", "STUDY", null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath(
						"$.message",
						containsString("available from 12:00 to 15:00")
				));
	}

	@Test
	void createRejectsRoundedSlotAfterWindowEnd() throws Exception {
		clock.setLocalDateTime(LocalDateTime.of(2026, 8, 14, 14, 45));

		mockMvc.perform(user.authorize(post("/api/lunch/requests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(lunchJson("IN_30_MINUTES", "STUDY", null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath(
						"$.message",
						containsString("outside the available window")
				));
	}

	@Test
	void createValidatesRequiredFields() throws Exception {
		mockMvc.perform(user.authorize(post("/api/lunch/requests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void cancelIsIdempotentAndRemovesRequestFromActiveLookup() throws Exception {
		JsonNode created = createRequest("NOW", "STUDY", null);

		mockMvc.perform(user.authorize(delete("/api/lunch/requests/me")))
				.andExpect(status().isNoContent());
		mockMvc.perform(user.authorize(delete("/api/lunch/requests/me")))
				.andExpect(status().isNoContent());
		mockMvc.perform(user.authorize(get("/api/lunch/requests/me")))
				.andExpect(status().isNotFound());

		LunchRequest cancelled = lunchRequestRepository.findById(
				UUID.fromString(created.path("id").asText())
		).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(cancelled.getStatus())
				.isEqualTo(LunchRequestStatus.CANCELLED);
		org.assertj.core.api.Assertions.assertThat(cancelled.getCancelledAt()).isNotNull();
	}

	@Test
	void extendIsIdempotentAndEnforcesConfiguredLimit() throws Exception {
		LunchRequest request = findRequest(createRequest("NOW", "STUDY", null));

		UUID firstOfferId = offerExtension(
				request,
				LocalDateTime.of(2026, 8, 14, 12, 15)
		);
		JsonNode firstExtension = responseFrom(extendRequest(firstOfferId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timeSlot").value("2026-08-14T12:25:00"))
				.andExpect(jsonPath("$.extensionCount").value(1))
				.andExpect(jsonPath("$.extensionOfferId")
						.value(firstOfferId.toString())));

		extendRequest(firstOfferId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(firstExtension.path("id").asText()))
				.andExpect(jsonPath("$.extensionCount").value(1));

		request = findRequest(firstExtension);
		UUID secondOfferId = offerExtension(
				request,
				LocalDateTime.of(2026, 8, 14, 12, 25)
		);
		JsonNode secondExtension = responseFrom(extendRequest(secondOfferId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timeSlot").value("2026-08-14T12:35:00"))
				.andExpect(jsonPath("$.extensionCount").value(2)));

		request = findRequest(secondExtension);
		UUID thirdOfferId = offerExtension(
				request,
				LocalDateTime.of(2026, 8, 14, 12, 35)
		);
		extendRequest(thirdOfferId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", containsString("extension limit: 2")));
	}

	@Test
	void extendRejectsExpiredOffer() throws Exception {
		LunchRequest request = findRequest(createRequest("NOW", "STUDY", null));
		UUID offerId = offerExtension(
				request,
				LocalDateTime.of(2026, 8, 14, 12, 15)
		);
		clock.setLocalDateTime(LocalDateTime.of(2026, 8, 14, 12, 20));

		extendRequest(offerId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", containsString("offer has expired")));
	}

	@Test
	void acceptedOfferRemainsIdempotentAfterItsDeadline() throws Exception {
		LunchRequest request = findRequest(createRequest("NOW", "STUDY", null));
		UUID offerId = offerExtension(
				request,
				LocalDateTime.of(2026, 8, 14, 12, 15)
		);

		extendRequest(offerId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.extensionCount").value(1));
		clock.setLocalDateTime(LocalDateTime.of(2026, 8, 14, 12, 30));

		extendRequest(offerId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timeSlot").value("2026-08-14T12:25:00"))
				.andExpect(jsonPath("$.extensionCount").value(1));
	}

	@Test
	void staleOfferCannotAcceptANewerOffer() throws Exception {
		LunchRequest request = findRequest(createRequest("NOW", "STUDY", null));
		UUID firstOfferId = offerExtension(
				request,
				LocalDateTime.of(2026, 8, 14, 12, 15)
		);
		JsonNode firstExtension = responseFrom(extendRequest(firstOfferId)
				.andExpect(status().isOk()));

		request = findRequest(firstExtension);
		UUID secondOfferId = offerExtension(
				request,
				LocalDateTime.of(2026, 8, 14, 12, 25)
		);

		extendRequest(firstOfferId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath(
						"$.message",
						containsString("does not match the current offer")
				));

		LunchRequest unchanged = lunchRequestRepository.findById(request.getId())
				.orElseThrow();
		org.assertj.core.api.Assertions.assertThat(unchanged.getStatus())
				.isEqualTo(LunchRequestStatus.EXTENSION_REQUESTED);
		org.assertj.core.api.Assertions.assertThat(unchanged.getExtensionCount())
				.isEqualTo(1);
		org.assertj.core.api.Assertions.assertThat(unchanged.getTimeSlot())
				.isEqualTo(LocalDateTime.of(2026, 8, 14, 12, 25));

		extendRequest(secondOfferId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timeSlot").value("2026-08-14T12:35:00"))
				.andExpect(jsonPath("$.extensionCount").value(2));
	}

	@Test
	void extendValidatesOfferId() throws Exception {
		mockMvc.perform(user.authorize(post("/api/lunch/requests/me/extend"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	private JsonNode createRequest(String time, String topic, String comment) throws Exception {
		String response = mockMvc.perform(user.authorize(post("/api/lunch/requests"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(lunchJson(time, topic, comment)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/lunch/requests/me"))
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response);
	}

	private LunchRequest findRequest(JsonNode response) {
		return lunchRequestRepository.findById(
				UUID.fromString(response.path("id").asText())
		).orElseThrow();
	}

	private UUID offerExtension(LunchRequest request, LocalDateTime offeredAt) {
		clock.setLocalDateTime(offeredAt);
		UUID offerId = UUID.randomUUID();
		request.setStatus(LunchRequestStatus.EXTENSION_REQUESTED);
		request.setExtensionOfferId(offerId);
		request.setExtensionRequestedAt(offeredAt);
		request.setExtensionExpiresAt(offeredAt.plusMinutes(5));
		request.setExtensionTargetTimeSlot(request.getTimeSlot().plusMinutes(10));
		lunchRequestRepository.saveAndFlush(request);
		return offerId;
	}

	private ResultActions extendRequest(UUID offerId) throws Exception {
		String content = objectMapper.writeValueAsString(
				objectMapper.createObjectNode().put("offerId", offerId.toString())
		);
		return mockMvc.perform(user.authorize(post("/api/lunch/requests/me/extend"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(content));
	}

	private JsonNode responseFrom(ResultActions actions) throws Exception {
		return objectMapper.readTree(
				actions.andReturn().getResponse().getContentAsString()
		);
	}

	private String lunchJson(String time, String topic, String comment) throws Exception {
		var payload = objectMapper.createObjectNode()
				.put("locationId", location.getId().toString())
				.put("time", time)
				.put("topic", topic);
		if (comment != null) {
			payload.put("comment", comment);
		}
		return objectMapper.writeValueAsString(payload);
	}

	private Location createLocation() {
		University university = new University();
		university.setName("Lunch University " + UUID.randomUUID());
		university.setCity("Saint Petersburg");
		university = universityRepository.saveAndFlush(university);

		Location lunchLocation = new Location();
		lunchLocation.setUniversity(university);
		lunchLocation.setType(LocationType.DINING_ROOM);
		lunchLocation.setName("Lunch location " + UUID.randomUUID());
		lunchLocation.setAddress("Kronverksky Prospekt 49");
		return locationRepository.saveAndFlush(lunchLocation);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {
		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(DEFAULT_INSTANT, LUNCH_ZONE);
		}
	}

	static final class MutableClock extends Clock {
		private final AtomicReference<Instant> instant;
		private final ZoneId zone;

		MutableClock(Instant instant, ZoneId zone) {
			this.instant = new AtomicReference<>(instant);
			this.zone = zone;
		}

		void setInstant(Instant value) {
			instant.set(value);
		}

		void setLocalDateTime(LocalDateTime value) {
			setInstant(value.atZone(zone).toInstant());
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId requestedZone) {
			return Clock.fixed(instant(), requestedZone);
		}

		@Override
		public Instant instant() {
			return instant.get();
		}
	}
}
