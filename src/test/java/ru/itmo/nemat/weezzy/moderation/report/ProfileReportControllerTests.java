package ru.itmo.nemat.weezzy.moderation.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.moderation.report.dto.CreateProfileReportRequest;
import ru.itmo.nemat.weezzy.moderation.report.dto.DecideProfileReportRequest;
import ru.itmo.nemat.weezzy.security.JwtService;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.user.UserRepository;
import tools.jackson.databind.ObjectMapper;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ProfileReportControllerTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");
	private static final String PASSWORD = "password123";

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
	private ProfileReportRepository reportRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private JwtService jwtService;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void clearReports() {
		reportRepository.deleteAll();
	}

	@Test
	void authenticatedUserCreatesReport() throws Exception {
		ReportActor reporter = registerActor("Report Creator");
		ReportActor target = registerActor("Report Target");

		mockMvc.perform(createReportRequest(
				reporter,
				target.profileId(),
				ProfileReportReason.SPAM,
				"Repeated unwanted messages"
		))
				.andExpect(status().isCreated())
				.andExpect(header().string(
						HttpHeaders.LOCATION,
						matchesPattern("/api/reports/.+")
				))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.reporterProfileId").value(reporter.profileId()))
				.andExpect(jsonPath("$.targetProfileId").value(target.profileId()))
				.andExpect(jsonPath("$.reason").value("SPAM"))
				.andExpect(jsonPath("$.comment").value("Repeated unwanted messages"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.decision").doesNotExist())
				.andExpect(jsonPath("$.reviewedByUserId").doesNotExist())
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty())
				.andExpect(jsonPath("$.reviewedAt").doesNotExist())
				.andExpect(jsonPath("$.closedAt").doesNotExist());
	}

	@Test
	void createReportTrimsComment() throws Exception {
		ReportActor reporter = registerActor("Trim Reporter");
		ReportActor target = registerActor("Trim Target");

		mockMvc.perform(createReportRequest(
				reporter,
				target.profileId(),
				ProfileReportReason.OTHER,
				"  Evidence described here  "
		))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comment").value("Evidence described here"));
	}

	@Test
	void reportsRequireAuthentication() throws Exception {
		ReportActor target = registerActor("Unauthorized Target");

		mockMvc.perform(post("/api/reports/{targetProfileId}", target.profileId())
						.contentType(MediaType.APPLICATION_JSON)
						.content(reportBody(ProfileReportReason.SPAM, null)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void createReportRequiresReporterProfile() throws Exception {
		AuthenticatedTestUser userWithoutProfile = AuthenticatedTestUser.register(
				mockMvc,
				objectMapper
		);
		ReportActor target = registerActor("Existing Target");

		mockMvc.perform(userWithoutProfile.authorize(post(
						"/api/reports/{targetProfileId}",
						target.profileId()
				))
						.contentType(MediaType.APPLICATION_JSON)
						.content(reportBody(ProfileReportReason.SPAM, null)))
				.andExpect(status().isNotFound());
	}

	@Test
	void createReportRejectsMissingReasonAndOversizedComment() throws Exception {
		ReportActor reporter = registerActor("Validation Reporter");
		ReportActor target = registerActor("Validation Target");

		mockMvc.perform(reporter.user().authorize(post(
						"/api/reports/{targetProfileId}",
						target.profileId()
				))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"comment\":\"No reason\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("reason")));

		mockMvc.perform(createReportRequest(
				reporter,
				target.profileId(),
				ProfileReportReason.SPAM,
				"x".repeat(1001)
		))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("comment")));
	}

	@Test
	void otherReasonRequiresComment() throws Exception {
		ReportActor reporter = registerActor("Other Reporter");
		ReportActor target = registerActor("Other Target");

		mockMvc.perform(createReportRequest(
				reporter,
				target.profileId(),
				ProfileReportReason.OTHER,
				null
		))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("Comment is required when report reason is OTHER"));

		mockMvc.perform(createReportRequest(
				reporter,
				target.profileId(),
				ProfileReportReason.OTHER,
				"   "
		))
				.andExpect(status().isBadRequest());
	}

	@Test
	void userCannotReportOwnProfile() throws Exception {
		ReportActor reporter = registerActor("Self Reporter");

		mockMvc.perform(createReportRequest(
				reporter,
				reporter.profileId(),
				ProfileReportReason.SPAM,
				null
		))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"A profile cannot report itself: " + reporter.profileId()
				));
	}

	@Test
	void createReportReturnsNotFoundForMissingTarget() throws Exception {
		ReportActor reporter = registerActor("Missing Target Reporter");

		mockMvc.perform(createReportRequest(
				reporter,
				"00000000-0000-0000-0000-000000000000",
				ProfileReportReason.FAKE_PROFILE,
				null
		))
				.andExpect(status().isNotFound());
	}

	@Test
	void duplicateOpenReportReturnsConflict() throws Exception {
		ReportActor reporter = registerActor("Duplicate Reporter");
		ReportActor target = registerActor("Duplicate Target");
		createReport(reporter, target.profileId(), ProfileReportReason.SPAM, null);

		mockMvc.perform(createReportRequest(
				reporter,
				target.profileId(),
				ProfileReportReason.HARASSMENT,
				"Second open report"
		))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString(
						"An open report from profile"
				)));
	}

	@Test
	void adminEndpointsRequireAdminRole() throws Exception {
		ReportActor reporter = registerActor("Protected Reporter");
		ReportActor target = registerActor("Protected Target");
		String reportId = createReport(
				reporter,
				target.profileId(),
				ProfileReportReason.SPAM,
				null
		);
		String reportUrl = "/api/admin/reports/" + reportId;
		String decision = decisionBody(ProfileReportStatus.REJECTED, "No violation");

		mockMvc.perform(get("/api/admin/reports"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get(reportUrl))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(patch(reportUrl + "/review"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(patch(reportUrl + "/decision")
						.contentType(MediaType.APPLICATION_JSON)
						.content(decision))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(reporter.user().authorize(get("/api/admin/reports")))
				.andExpect(status().isForbidden());
		mockMvc.perform(reporter.user().authorize(get(reportUrl)))
				.andExpect(status().isForbidden());
		mockMvc.perform(reporter.user().authorize(patch(reportUrl + "/review")))
				.andExpect(status().isForbidden());
		mockMvc.perform(reporter.user().authorize(patch(reportUrl + "/decision"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(decision))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminListsReportsByStatusWithPaginationAndGetsDetails() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		ReportActor firstReporter = registerActor("Queue Reporter One");
		ReportActor firstTarget = registerActor("Queue Target One");
		ReportActor secondReporter = registerActor("Queue Reporter Two");
		ReportActor secondTarget = registerActor("Queue Target Two");
		ReportActor resolvedReporter = registerActor("Resolved Reporter");
		ReportActor resolvedTarget = registerActor("Resolved Target");

		String firstPendingId = createReport(
				firstReporter,
				firstTarget.profileId(),
				ProfileReportReason.SPAM,
				"First pending"
		);
		createReport(
				secondReporter,
				secondTarget.profileId(),
				ProfileReportReason.HARASSMENT,
				"Second pending"
		);
		String resolvedId = createReport(
				resolvedReporter,
				resolvedTarget.profileId(),
				ProfileReportReason.FAKE_PROFILE,
				"Resolved report"
		);
		decide(admin, resolvedId, ProfileReportStatus.RESOLVED, "Fake profile confirmed");

		mockMvc.perform(admin.authorize(get("/api/admin/reports")
						.param("status", "PENDING")
						.param("page", "0")
						.param("size", "1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[*].status").value(everyItem(is("PENDING"))))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.hasNext").value(true))
				.andExpect(jsonPath("$.hasPrevious").value(false));

		mockMvc.perform(admin.authorize(get("/api/admin/reports")
						.param("status", "RESOLVED")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(resolvedId))
				.andExpect(jsonPath("$.content[0].status").value("RESOLVED"));

		mockMvc.perform(admin.authorize(get("/api/admin/reports/{reportId}", firstPendingId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(firstPendingId))
				.andExpect(jsonPath("$.comment").value("First pending"));
	}

	@Test
	void adminMarksPendingReportAsReviewed() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		ReportActor reporter = registerActor("Review Reporter");
		ReportActor target = registerActor("Review Target");
		String reportId = createReport(
				reporter,
				target.profileId(),
				ProfileReportReason.INAPPROPRIATE_CONTENT,
				"Review evidence"
		);

		mockMvc.perform(admin.authorize(patch(
						"/api/admin/reports/{reportId}/review",
						reportId
				)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVIEWED"))
				.andExpect(jsonPath("$.reviewedByUserId").value(admin.userId()))
				.andExpect(jsonPath("$.reviewedAt").isNotEmpty())
				.andExpect(jsonPath("$.closedAt").doesNotExist());

		mockMvc.perform(admin.authorize(patch(
						"/api/admin/reports/{reportId}/review",
						reportId
				)))
				.andExpect(status().isConflict());
	}

	@Test
	void adminDecidesPendingAndReviewedReports() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		ReportActor pendingReporter = registerActor("Pending Decision Reporter");
		ReportActor pendingTarget = registerActor("Pending Decision Target");
		ReportActor reviewedReporter = registerActor("Reviewed Decision Reporter");
		ReportActor reviewedTarget = registerActor("Reviewed Decision Target");
		String pendingId = createReport(
				pendingReporter,
				pendingTarget.profileId(),
				ProfileReportReason.SPAM,
				null
		);
		String reviewedId = createReport(
				reviewedReporter,
				reviewedTarget.profileId(),
				ProfileReportReason.HARASSMENT,
				"Needs review"
		);
		markReviewed(admin, reviewedId);

		mockMvc.perform(decideRequest(
				admin,
				pendingId,
				ProfileReportStatus.RESOLVED,
				"  Violation confirmed  "
		))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RESOLVED"))
				.andExpect(jsonPath("$.decision").value("Violation confirmed"))
				.andExpect(jsonPath("$.reviewedByUserId").value(admin.userId()))
				.andExpect(jsonPath("$.reviewedAt").isNotEmpty())
				.andExpect(jsonPath("$.closedAt").isNotEmpty());

		mockMvc.perform(decideRequest(
				admin,
				reviewedId,
				ProfileReportStatus.REJECTED,
				"No violation found"
		))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.decision").value("No violation found"));
	}

	@Test
	void decisionValidatesFinalStatusAndText() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		ReportActor reporter = registerActor("Decision Validation Reporter");
		ReportActor target = registerActor("Decision Validation Target");
		String reportId = createReport(
				reporter,
				target.profileId(),
				ProfileReportReason.SPAM,
				null
		);

		mockMvc.perform(decideRequest(
				admin,
				reportId,
				ProfileReportStatus.PENDING,
				"Invalid final status"
		))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString(
						"must be REJECTED or RESOLVED"
				)));

		mockMvc.perform(decideRequest(
				admin,
				reportId,
				ProfileReportStatus.REJECTED,
				"   "
		))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("decision")));

		mockMvc.perform(decideRequest(
				admin,
				reportId,
				ProfileReportStatus.REJECTED,
				"x".repeat(1001)
		))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("decision")));
	}

	@Test
	void closedReportCannotBeReviewedOrDecidedAgain() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		ReportActor reporter = registerActor("Closed Reporter");
		ReportActor target = registerActor("Closed Target");
		String reportId = createReport(
				reporter,
				target.profileId(),
				ProfileReportReason.SPAM,
				null
		);
		decide(admin, reportId, ProfileReportStatus.REJECTED, "No violation");

		mockMvc.perform(admin.authorize(patch(
						"/api/admin/reports/{reportId}/review",
						reportId
				)))
				.andExpect(status().isConflict());

		mockMvc.perform(decideRequest(
				admin,
				reportId,
				ProfileReportStatus.RESOLVED,
				"Changed decision"
		))
				.andExpect(status().isConflict());
	}

	@Test
	void reporterCanCreateNewReportAfterPreviousOneIsClosed() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		ReportActor reporter = registerActor("Repeat Reporter");
		ReportActor target = registerActor("Repeat Target");
		String firstReportId = createReport(
				reporter,
				target.profileId(),
				ProfileReportReason.SPAM,
				null
		);
		decide(admin, firstReportId, ProfileReportStatus.REJECTED, "No violation");

		mockMvc.perform(createReportRequest(
				reporter,
				target.profileId(),
				ProfileReportReason.HARASSMENT,
				"New incident"
		))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(not(is(firstReportId))));
	}

	@Test
	void adminEndpointsReturnNotFoundForMissingReport() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		String missingId = "00000000-0000-0000-0000-000000000000";

		mockMvc.perform(admin.authorize(get(
						"/api/admin/reports/{reportId}",
						missingId
				)))
				.andExpect(status().isNotFound());

		mockMvc.perform(admin.authorize(patch(
						"/api/admin/reports/{reportId}/review",
						missingId
				)))
				.andExpect(status().isNotFound());

		mockMvc.perform(decideRequest(
				admin,
				missingId,
				ProfileReportStatus.REJECTED,
				"Missing report"
		))
				.andExpect(status().isNotFound());
	}

	@Test
	void reportSurvivesTargetAccountDeletion() throws Exception {
		AuthenticatedTestUser admin = registerAdmin();
		ReportActor reporter = registerActor("Deletion Reporter");
		ReportActor target = registerActor("Deletion Target");
		String reportId = createReport(
				reporter,
				target.profileId(),
				ProfileReportReason.FAKE_PROFILE,
				"Evidence before deletion"
		);

		mockMvc.perform(target.user().authorize(delete("/api/users/me"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isNoContent());

		mockMvc.perform(admin.authorize(get(
						"/api/admin/reports/{reportId}",
						reportId
				)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.targetProfileId").value(target.profileId()))
				.andExpect(jsonPath("$.comment").value("Evidence before deletion"));

		ReportActor anotherReporter = registerActor("Deleted Target Reporter");
		mockMvc.perform(createReportRequest(
				anotherReporter,
				target.profileId(),
				ProfileReportReason.SPAM,
				null
		))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString(
						"Cannot interact with deleted profile"
				)));
	}

	private ReportActor registerActor(String displayName) throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser.TestProfile profile = user.createProfile(displayName);
		return new ReportActor(user, profile.id());
	}

	private AuthenticatedTestUser registerAdmin() throws Exception {
		return AuthenticatedTestUser.registerAdmin(
				mockMvc,
				objectMapper,
				userRepository,
				jwtService
		);
	}

	private String createReport(
			ReportActor reporter,
			String targetProfileId,
			ProfileReportReason reason,
			String comment
	) throws Exception {
		String response = mockMvc.perform(createReportRequest(
				reporter,
				targetProfileId,
				reason,
				comment
		))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		return objectMapper.readTree(response).path("id").asText();
	}

	private MockHttpServletRequestBuilder createReportRequest(
			ReportActor reporter,
			String targetProfileId,
			ProfileReportReason reason,
			String comment
	) throws Exception {
		return reporter.user().authorize(post(
						"/api/reports/{targetProfileId}",
						targetProfileId
				))
				.contentType(MediaType.APPLICATION_JSON)
				.content(reportBody(reason, comment));
	}

	private String reportBody(ProfileReportReason reason, String comment) throws Exception {
		return objectMapper.writeValueAsString(new CreateProfileReportRequest(reason, comment));
	}

	private void markReviewed(AuthenticatedTestUser admin, String reportId) throws Exception {
		mockMvc.perform(admin.authorize(patch(
						"/api/admin/reports/{reportId}/review",
						reportId
				)))
				.andExpect(status().isOk());
	}

	private void decide(
			AuthenticatedTestUser admin,
			String reportId,
			ProfileReportStatus status,
			String decision
	) throws Exception {
		mockMvc.perform(decideRequest(admin, reportId, status, decision))
				.andExpect(status().isOk());
	}

	private MockHttpServletRequestBuilder decideRequest(
			AuthenticatedTestUser admin,
			String reportId,
			ProfileReportStatus status,
			String decision
	) throws Exception {
		return admin.authorize(patch(
						"/api/admin/reports/{reportId}/decision",
						reportId
				))
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionBody(status, decision));
	}

	private String decisionBody(
			ProfileReportStatus status,
			String decision
	) throws Exception {
		return objectMapper.writeValueAsString(
				new DecideProfileReportRequest(status, decision)
		);
	}

	private record ReportActor(AuthenticatedTestUser user, String profileId) {
	}
}
