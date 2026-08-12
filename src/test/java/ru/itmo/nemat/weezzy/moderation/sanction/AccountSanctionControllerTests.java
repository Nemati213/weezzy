package ru.itmo.nemat.weezzy.moderation.sanction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.moderation.report.ProfileReportReason;
import ru.itmo.nemat.weezzy.moderation.report.ProfileReportRepository;
import ru.itmo.nemat.weezzy.moderation.report.ProfileReportStatus;
import ru.itmo.nemat.weezzy.moderation.report.dto.CreateProfileReportRequest;
import ru.itmo.nemat.weezzy.moderation.report.dto.DecideProfileReportRequest;
import ru.itmo.nemat.weezzy.moderation.sanction.dto.CreateAccountSanctionRequest;
import ru.itmo.nemat.weezzy.moderation.sanction.dto.RevokeAccountSanctionRequest;
import ru.itmo.nemat.weezzy.security.JwtService;
import ru.itmo.nemat.weezzy.security.session.AuthSessionRepository;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserRepository;
import ru.itmo.nemat.weezzy.user.accountdeletion.AccountDeletionService;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "app.security.access-token-revocation.enabled=true")
@AutoConfigureMockMvc
class AccountSanctionControllerTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");
	private static final String PASSWORD = "password123";

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine")
			.withExposedPorts(6379);

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private AccountSanctionRepository sanctionRepository;
	@Autowired
	private AccountSanctionService sanctionService;
	@Autowired
	private ProfileReportRepository reportRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private JwtService jwtService;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private AuthSessionRepository authSessionRepository;
	@Autowired
	private AccountDeletionService accountDeletionService;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
	}

	@BeforeEach
	void clearModerationData() {
		sanctionRepository.deleteAll();
		reportRepository.deleteAll();
	}

	@Test
	void adminCreatesTemporarySuspension() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		SanctionTarget target = registerTarget("Temporary Suspension Target");
		LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

		mockMvc.perform(createSanctionRequest(
				admin,
				target.user().userId(),
				AccountSanctionType.TEMPORARY_SUSPENSION,
				"Repeated harassment",
				expiresAt,
				null
		))
				.andExpect(status().isCreated())
				.andExpect(header().string(
						HttpHeaders.LOCATION,
						matchesPattern("/api/admin/sanctions/.+")
				))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.targetUserId").value(target.user().userId()))
				.andExpect(jsonPath("$.targetProfileId").value(target.profileId()))
				.andExpect(jsonPath("$.type").value("TEMPORARY_SUSPENSION"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.reason").value("Repeated harassment"))
				.andExpect(jsonPath("$.expiresAt").isNotEmpty())
				.andExpect(jsonPath("$.createdByUserId").value(admin.userId()))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.revokedAt").doesNotExist());
	}

	@Test
	void adminCreatesPermanentBanForUserWithoutProfile() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		AuthenticatedTestUser target = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(createSanctionRequest(
				admin,
				target.userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Severe repeated abuse",
				null,
				null
		))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.targetUserId").value(target.userId()))
				.andExpect(jsonPath("$.targetProfileId").doesNotExist())
				.andExpect(jsonPath("$.type").value("PERMANENT_BAN"))
				.andExpect(jsonPath("$.expiresAt").doesNotExist());
	}

	@Test
	void sanctionRevokesAccessLoginRefreshAndServerSessions() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		SanctionTarget target = registerTarget("Access Restricted Target");
		User targetUser = userRepository.findById(
				UUID.fromString(target.user().userId())
		).orElseThrow();
		String loginResponse = mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "email": "%s",
							  "password": "%s"
							}
							""".formatted(targetUser.getEmail(), PASSWORD)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String refreshToken = objectMapper.readTree(loginResponse)
				.path("refreshToken")
				.asText();

		createSanction(
				admin,
				target.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Serious policy violation",
				null,
				null
		);

		mockMvc.perform(target.user().authorize(get("/api/auth/me")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(
						"Account access is restricted by a sanction"
				));

		mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "email": "%s",
							  "password": "%s"
							}
							""".formatted(targetUser.getEmail(), PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(
						"Account is permanently banned: Serious policy violation"
				));

		mockMvc.perform(post("/api/auth/refresh")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(
							java.util.Map.of("refreshToken", refreshToken)
					)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(
						"Account is permanently banned: Serious policy violation"
				));

		assertThat(authSessionRepository.findAll()).filteredOn(session ->
					session.getUser().getId().equals(targetUser.getId())
		).allSatisfy(session -> {
			assertThat(session.getRevokedAt()).isNotNull();
			assertThat(session.getRevokeReason()).isEqualTo("ACCOUNT_SANCTION");
		});
	}

	@Test
	void sanctionHidesProfileRejectsVotesAndHidesExistingMatch() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		SanctionTarget viewer = registerTarget("Sanction Visibility Viewer");
		SanctionTarget target = registerTarget("Sanction Hidden Target");

		like(viewer, target);
		like(target, viewer);
		mockMvc.perform(viewer.user().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1));

		createSanction(
				admin,
				target.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Hide sanctioned profile",
				null,
				null
		);

		mockMvc.perform(viewer.user().authorize(get(
				"/api/profiles/{profileId}",
				target.profileId()
		)))
				.andExpect(status().isNotFound());
		mockMvc.perform(viewer.user().authorize(get("/api/profiles")))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString(
						"Sanction Hidden Target"
				))));
		mockMvc.perform(viewer.user().authorize(post(
						"/api/votes/{profileId}",
						target.profileId()
				))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"LIKE\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(viewer.user().authorize(get("/api/matches")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());

		Integer storedMatches = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM profile_matches",
				Integer.class
		);
		assertThat(storedMatches).isEqualTo(1);

		mockMvc.perform(viewer.user().authorize(post(
				"/api/blocks/{profileId}",
				target.profileId()
		)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blockedProfile.id").value(target.profileId()))
				.andExpect(jsonPath("$.blockedProfile.displayName")
						.value("Unavailable profile"))
				.andExpect(jsonPath("$.blockedProfile.userId").doesNotExist())
				.andExpect(jsonPath("$.blockedProfile.photos").isEmpty());
		mockMvc.perform(viewer.user().authorize(get("/api/blocks")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].blockedProfile.displayName")
						.value("Unavailable profile"));
		mockMvc.perform(viewer.user().authorize(delete(
				"/api/blocks/{profileId}",
				target.profileId()
		)))
				.andExpect(status().isNoContent());
	}

	@Test
	void sanctionCreationRequiresAdminRole() throws Exception {
		SanctionTarget target = registerTarget("Protected Sanction Target");
		CreateAccountSanctionRequest request = new CreateAccountSanctionRequest(
				AccountSanctionType.PERMANENT_BAN,
				"Protected action",
				null,
				null
		);
		String body = objectMapper.writeValueAsString(request);
		String url = "/api/admin/users/" + target.user().userId() + "/sanctions";

		mockMvc.perform(post(url)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(target.user().authorize(post(url))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/admin/sanctions"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(target.user().authorize(get("/api/admin/sanctions")))
				.andExpect(status().isForbidden());
	}

	@Test
	void sanctionCreationValidatesExpirationAndReason() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		SanctionTarget target = registerTarget("Expiration Validation Target");

		mockMvc.perform(createSanctionRequest(
				admin,
				target.user().userId(),
				AccountSanctionType.TEMPORARY_SUSPENSION,
				"Missing expiration",
				null,
				null
		))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"Temporary suspension expiration must be in the future"
				));

		mockMvc.perform(createSanctionRequest(
				admin,
				target.user().userId(),
				AccountSanctionType.TEMPORARY_SUSPENSION,
				"Past expiration",
				LocalDateTime.now().minusMinutes(1),
				null
		))
				.andExpect(status().isBadRequest());

		mockMvc.perform(createSanctionRequest(
				admin,
				target.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Unexpected expiration",
				LocalDateTime.now().plusDays(1),
				null
		))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"Permanent ban must not have an expiration"
				));

		mockMvc.perform(createSanctionRequest(
				admin,
				target.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"   ",
				null,
				null
		))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("reason")));
	}

	@Test
	void adminCannotSanctionSelfOrAnotherAdmin() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		AuthenticatedTestUser anotherAdmin = registerAdmin();

		mockMvc.perform(createSanctionRequest(
				admin,
				admin.userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Self sanction",
				null,
				null
		))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString(
						"cannot sanction their own account"
				)));

		mockMvc.perform(createSanctionRequest(
				admin,
				anotherAdmin.userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Admin target",
				null,
				null
		))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString(
						"Administrator accounts cannot be sanctioned"
				)));
	}

	@Test
	void sanctionCreationReturnsNotFoundForMissingUser() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();

		mockMvc.perform(createSanctionRequest(
				admin,
				"00000000-0000-0000-0000-000000000000",
				AccountSanctionType.PERMANENT_BAN,
				"Missing target",
				null,
				null
		))
				.andExpect(status().isNotFound());
	}

	@Test
	void duplicateActiveSanctionReturnsConflict() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		SanctionTarget target = registerTarget("Duplicate Sanction Target");
		createSanction(
				admin,
				target.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"First sanction",
				null,
				null
		);

		mockMvc.perform(createSanctionRequest(
				admin,
				target.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Second sanction",
				null,
				null
		))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString(
						"already has an active account sanction"
				)));
	}

	@Test
	void adminRevokesSanctionAndCanCreateAnother() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		SanctionTarget target = registerTarget("Revoked Sanction Target");
		String targetEmail = userRepository.findById(
				UUID.fromString(target.user().userId())
		).orElseThrow().getEmail();
		String firstId = createSanction(
				admin,
				target.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Initial sanction",
				null,
				null
		);

		mockMvc.perform(revokeRequest(admin, firstId, "Decision reconsidered"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVOKED"))
				.andExpect(jsonPath("$.revokedByUserId").value(admin.userId()))
				.andExpect(jsonPath("$.revokedAt").isNotEmpty())
				.andExpect(jsonPath("$.revocationReason")
						.value("Decision reconsidered"));

		login(targetEmail)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
		mockMvc.perform(admin.authorize(get(
				"/api/profiles/{profileId}",
				target.profileId()
		)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Revoked Sanction Target"));

		mockMvc.perform(createSanctionRequest(
				admin,
				target.user().userId(),
				AccountSanctionType.TEMPORARY_SUSPENSION,
				"New incident",
				LocalDateTime.now().plusDays(1),
				null
		))
				.andExpect(status().isCreated());
	}

	@Test
	void revokeValidatesStateReasonAndMissingId() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		SanctionTarget target = registerTarget("Revoke Validation Target");
		String sanctionId = createSanction(
				admin,
				target.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Sanction to revoke",
				null,
				null
		);

		mockMvc.perform(revokeRequest(admin, sanctionId, "   "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("reason")));

		mockMvc.perform(revokeRequest(admin, sanctionId, "First revocation"))
				.andExpect(status().isOk());
		mockMvc.perform(revokeRequest(admin, sanctionId, "Second revocation"))
				.andExpect(status().isConflict());

		mockMvc.perform(revokeRequest(
				admin,
				"00000000-0000-0000-0000-000000000000",
				"Missing sanction"
		))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminListsSanctionsByStatusAndTargetHistory() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		SanctionTarget firstTarget = registerTarget("Sanction Queue Target One");
		SanctionTarget secondTarget = registerTarget("Sanction Queue Target Two");
		String revokedId = createSanction(
				admin,
				firstTarget.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Revoked queue sanction",
				null,
				null
		);
		revoke(admin, revokedId, "Queue revocation");
		String activeId = createSanction(
				admin,
				secondTarget.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Active queue sanction",
				null,
				null
		);

		mockMvc.perform(admin.authorize(get("/api/admin/sanctions")
						.param("status", "ACTIVE")
						.param("size", "1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(activeId))
				.andExpect(jsonPath("$.content[*].status").value(everyItem(is("ACTIVE"))))
				.andExpect(jsonPath("$.totalElements").value(1));

		mockMvc.perform(admin.authorize(get("/api/admin/sanctions")
						.param("status", "REVOKED")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(revokedId));

		mockMvc.perform(admin.authorize(get(
						"/api/admin/users/{targetUserId}/sanctions",
						firstTarget.user().userId()
				)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(revokedId));

		mockMvc.perform(admin.authorize(get(
						"/api/admin/sanctions/{sanctionId}",
						activeId
				)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(activeId));
	}

	@Test
	void expiredTemporarySanctionIsMarkedExpiredAndCanBeReplaced() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		SanctionTarget target = registerTarget("Expired Sanction Target");
		String targetEmail = userRepository.findById(
				UUID.fromString(target.user().userId())
		).orElseThrow().getEmail();
		String sanctionId = createSanction(
				admin,
				target.user().userId(),
				AccountSanctionType.TEMPORARY_SUSPENSION,
				"Temporary sanction",
				LocalDateTime.now().plusDays(1),
				null
		);
		jdbcTemplate.update(
				"UPDATE account_sanctions SET expires_at = ? WHERE id = ?",
				LocalDateTime.now().minusMinutes(1),
				UUID.fromString(sanctionId)
		);
		assertThat(sanctionService.findEffectiveByTargetUserId(
						UUID.fromString(target.user().userId())
				)).isEmpty();

		mockMvc.perform(admin.authorize(get(
						"/api/admin/sanctions/{sanctionId}",
						sanctionId
				)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EXPIRED"));
		login(targetEmail)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
		mockMvc.perform(admin.authorize(get(
				"/api/profiles/{profileId}",
				target.profileId()
		)))
				.andExpect(status().isOk());

		mockMvc.perform(createSanctionRequest(
				admin,
				target.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Replacement sanction",
				null,
				null
		))
				.andExpect(status().isCreated());
	}

	@Test
	void sanctionCanReferenceResolvedReportForSameTarget() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		SanctionTarget reporter = registerTarget("Source Report Reporter");
		SanctionTarget target = registerTarget("Source Report Target");
		String reportId = createReport(reporter, target, ProfileReportReason.HARASSMENT);
		decideReport(admin, reportId, ProfileReportStatus.RESOLVED, "Violation confirmed");

		mockMvc.perform(createSanctionRequest(
				admin,
				target.user().userId(),
				AccountSanctionType.TEMPORARY_SUSPENSION,
				"Harassment confirmed",
				LocalDateTime.now().plusDays(3),
				reportId
		))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sourceReportId").value(reportId));
	}

	@Test
	void sourceReportMustBeResolvedAndTargetSameUser() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		SanctionTarget reporter = registerTarget("Mismatch Report Reporter");
		SanctionTarget reportTarget = registerTarget("Mismatch Report Target");
		SanctionTarget otherTarget = registerTarget("Other Sanction Target");
		String reportId = createReport(
				reporter,
				reportTarget,
				ProfileReportReason.HARASSMENT
		);

		mockMvc.perform(createSanctionRequest(
				admin,
				reportTarget.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Pending report",
				null,
				reportId
		))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString(
						"must be RESOLVED"
				)));

		decideReport(admin, reportId, ProfileReportStatus.RESOLVED, "Confirmed");
		mockMvc.perform(createSanctionRequest(
				admin,
				otherTarget.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Wrong target",
				null,
				reportId
		))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString(
						"does not target user"
				)));
	}

	@Test
	void sanctionAuditSurvivesForcedTargetAccountDeletion() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		SanctionTarget target = registerTarget("Deleted Sanction Target");
		String sanctionId = createSanction(
				admin,
				target.user().userId(),
				AccountSanctionType.PERMANENT_BAN,
				"Audit must survive",
				null,
				null
		);

		accountDeletionService.deleteAccount(
				UUID.fromString(target.user().userId()),
				PASSWORD
		);

		mockMvc.perform(admin.authorize(get(
						"/api/admin/sanctions/{sanctionId}",
						sanctionId
				)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetUserId").value(target.user().userId()))
				.andExpect(jsonPath("$.targetProfileId").value(target.profileId()))
				.andExpect(jsonPath("$.reason").value("Audit must survive"));
	}

	private SanctionTarget registerTarget(String displayName) throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser.TestProfile profile = user.createProfile(displayName);
		return new SanctionTarget(user, profile.id());
	}

	private AuthenticatedTestUser registerAdmin() throws Exception {
		return AuthenticatedTestUser.registerAdmin(
				mockMvc,
				objectMapper,
				userRepository,
				jwtService
		);
	}

	private String createSanction(
			AuthenticatedTestUser admin,
			String targetUserId,
			AccountSanctionType type,
			String reason,
			LocalDateTime expiresAt,
			String sourceReportId
	) throws Exception {
		String response = mockMvc.perform(createSanctionRequest(
				admin,
				targetUserId,
				type,
				reason,
				expiresAt,
				sourceReportId
		))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response).path("id").asText();
	}

	private MockHttpServletRequestBuilder createSanctionRequest(
			AuthenticatedTestUser admin,
			String targetUserId,
			AccountSanctionType type,
			String reason,
			LocalDateTime expiresAt,
			String sourceReportId
	) throws Exception {
		CreateAccountSanctionRequest request = new CreateAccountSanctionRequest(
				type,
				reason,
				expiresAt,
				sourceReportId == null ? null : UUID.fromString(sourceReportId)
		);
		return admin.authorize(post(
						"/api/admin/users/{targetUserId}/sanctions",
						targetUserId
				))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request));
	}

	private MockHttpServletRequestBuilder revokeRequest(
			AuthenticatedTestUser admin,
			String sanctionId,
			String reason
	) throws Exception {
		return admin.authorize(patch(
						"/api/admin/sanctions/{sanctionId}/revoke",
						sanctionId
				))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						new RevokeAccountSanctionRequest(reason)
				));
	}

	private void revoke(
			AuthenticatedTestUser admin,
			String sanctionId,
			String reason
	) throws Exception {
		mockMvc.perform(revokeRequest(admin, sanctionId, reason))
				.andExpect(status().isOk());
	}

	private String createReport(
			SanctionTarget reporter,
			SanctionTarget target,
			ProfileReportReason reason
	) throws Exception {
		String response = mockMvc.perform(reporter.user().authorize(post(
						"/api/reports/{targetProfileId}",
						target.profileId()
				))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new CreateProfileReportRequest(reason, "Report evidence")
						)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response).path("id").asText();
	}

	private void decideReport(
			AuthenticatedTestUser admin,
			String reportId,
			ProfileReportStatus status,
			String decision
	) throws Exception {
		mockMvc.perform(admin.authorize(patch(
						"/api/admin/reports/{reportId}/decision",
						reportId
				))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new DecideProfileReportRequest(status, decision)
						)))
				.andExpect(status().isOk());
	}

	private void like(SanctionTarget source, SanctionTarget target) throws Exception {
		mockMvc.perform(source.user().authorize(post(
						"/api/votes/{targetProfileId}",
						target.profileId()
				))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"action\":\"LIKE\"}"))
				.andExpect(status().isOk());
	}

	private ResultActions login(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "%s"
						}
						""".formatted(email, PASSWORD)));
	}

	private record SanctionTarget(AuthenticatedTestUser user, String profileId) {
	}
}
