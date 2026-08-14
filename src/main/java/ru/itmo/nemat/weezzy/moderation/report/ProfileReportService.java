package ru.itmo.nemat.weezzy.moderation.report;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.ProfilePairLockService;
import ru.itmo.nemat.weezzy.moderation.report.dto.CreateProfileReportRequest;
import ru.itmo.nemat.weezzy.outbox.OutboxEventService;
import ru.itmo.nemat.weezzy.outbox.payload.ProfileReportDecidedPayload;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserService;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileReportService {
	private static final Set<ProfileReportStatus> OPEN_REPORT_STATUSES = Set.of(
			ProfileReportStatus.PENDING,
			ProfileReportStatus.REVIEWED
	);

	private static final Set<ProfileReportStatus> ALLOWED_FINAL_STATUSES = Set.of(
			ProfileReportStatus.RESOLVED,
			ProfileReportStatus.REJECTED
	);

	private final ProfileReportRepository reportRepository;
	private final ProfileService profileService;
	private final ProfilePairLockService pairLockService;
	private final UserService userService;
	private final OutboxEventService outboxEventService;

	@Transactional
	public ProfileReport create(
			UUID reporterProfileId,
			UUID targetProfileId,
			CreateProfileReportRequest request
	) {
		if (reporterProfileId.equals(targetProfileId)) {
			throw new SelfReportException(reporterProfileId);
		}

		pairLockService.lock(reporterProfileId, targetProfileId);

		Profile reporterProfile = profileService.findNotDeletedById(reporterProfileId);
		Profile targetProfile = profileService.findNotDeletedById(targetProfileId);

		validateRequest(request);
		ensureNoOpenReport(reporterProfileId, targetProfileId);

		return createReport(reporterProfile, targetProfile, request);
	}

	@Transactional(readOnly = true)
	public ProfileReport findById(UUID profileReportId) {
		return reportRepository.findById(profileReportId)
				.orElseThrow(() -> new ProfileReportNotFoundException(profileReportId));
	}

	@Transactional(readOnly = true)
	public boolean hasMatchingDecision(
			UUID reportId,
			UUID recipientUserId,
			UUID targetProfileId,
			ProfileReportStatus status,
			String decision
	) {
		return reportRepository.findById(reportId)
				.filter(report -> report.getStatus() == status)
				.filter(report -> Objects.equals(report.getDecision(), decision))
				.filter(report -> report.getTargetProfile().getId().equals(
						targetProfileId
				))
				.map(ProfileReport::getReporterProfile)
				.map(Profile::getUser)
				.map(User::getId)
				.filter(recipientUserId::equals)
				.isPresent();
	}

	@Transactional(readOnly = true)
	public Page<ProfileReport> findByStatus(
			ProfileReportStatus status,
			Pageable pageable
	) {
		return reportRepository.findByStatusOrderByCreatedAtAscIdAsc(status, pageable);
	}

	@Transactional
	public ProfileReport markReviewed(UUID profileReportId, UUID moderatorUserId) {
		User moderator = userService.findAdminById(moderatorUserId);
		ProfileReport report = findByIdForUpdate(profileReportId);
		if (report.getStatus() != ProfileReportStatus.PENDING) {
			throw new ProfileReportStatusConflictException(
					profileReportId,
					report.getStatus(),
					"marked as reviewed"
			);
		}

		report.setReviewedAt(LocalDateTime.now());
		report.setStatus(ProfileReportStatus.REVIEWED);
		report.setReviewedBy(moderator);
		return reportRepository.save(report);
	}

	@Transactional
	public ProfileReport decide(
			UUID profileReportId,
			UUID moderatorUserId,
			ProfileReportStatus finalStatus,
			String decision
	) {
		validateFinalStatus(finalStatus);
		validateDecision(decision);

		User moderator = userService.findAdminById(moderatorUserId);
		ProfileReport report = findByIdForUpdate(profileReportId);
		ensureOpen(report);

		ProfileReport decidedReport = applyDecision(
				report,
				moderator,
				finalStatus,
				decision
		);
		publishDecision(decidedReport);
		return decidedReport;
	}

	private void validateFinalStatus(ProfileReportStatus finalStatus) {
		if (!ALLOWED_FINAL_STATUSES.contains(finalStatus)) {
			throw new InvalidProfileReportStatusException(finalStatus);
		}
	}

	private void validateDecision(String decision) {
		if (decision == null || decision.isBlank()) {
			throw new ProfileReportDecisionRequiredException();
		}
	}

	private void ensureOpen(ProfileReport report) {
		if (!OPEN_REPORT_STATUSES.contains(report.getStatus())) {
			throw new ProfileReportStatusConflictException(
					report.getId(),
					report.getStatus(),
					"decided"
			);
		}
	}

	private ProfileReport findByIdForUpdate(UUID profileReportId) {
		return reportRepository.findByIdForUpdate(profileReportId)
				.orElseThrow(() -> new ProfileReportNotFoundException(profileReportId));
	}

	private ProfileReport applyDecision(
			ProfileReport report,
			User moderator,
			ProfileReportStatus finalStatus,
			String decision
	) {
		LocalDateTime now = LocalDateTime.now();

		if (report.getReviewedAt() == null) {
			report.setReviewedAt(now);
		}

		report.setStatus(finalStatus);
		report.setDecision(decision.trim());
		report.setReviewedBy(moderator);
		report.setClosedAt(now);

		return reportRepository.save(report);
	}

	private void publishDecision(ProfileReport report) {
		UUID reporterProfileId = report.getReporterProfile().getId();
		profileService.findOptionalOwnerUserId(reporterProfileId)
				.ifPresent(recipientUserId -> outboxEventService.publish(
						new ProfileReportDecidedPayload(
								report.getId(),
								recipientUserId,
								report.getTargetProfile().getId(),
								report.getStatus(),
								report.getDecision()
						)
				));
	}

	private void validateRequest(CreateProfileReportRequest request) {
		if (request.reason() == ProfileReportReason.OTHER
				&& (request.comment() == null || request.comment().isBlank())) {
			throw new ProfileReportCommentRequiredException();
		}
	}

	private void ensureNoOpenReport(UUID reporterProfileId, UUID targetProfileId) {
		if (reportRepository.existsByReporterProfileIdAndTargetProfileIdAndStatusIn(
				reporterProfileId,
				targetProfileId,
				OPEN_REPORT_STATUSES
		)) {
			throw new DuplicateOpenProfileReportException(
					reporterProfileId,
					targetProfileId
			);
		}
	}

	private ProfileReport createReport(
			Profile reporterProfile,
			Profile targetProfile,
			CreateProfileReportRequest request
	) {
		ProfileReport report = new ProfileReport();
		report.setReporterProfile(reporterProfile);
		report.setTargetProfile(targetProfile);
		report.setStatus(ProfileReportStatus.PENDING);
		report.setReason(request.reason());
		report.setComment(normalizeComment(request.comment()));
		return reportRepository.save(report);
	}

	private String normalizeComment(String comment) {
		if (comment == null || comment.isBlank()) {
			return null;
		}
		return comment.trim();
	}
}
