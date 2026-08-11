package ru.itmo.nemat.weezzy.moderation.report;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.moderation.report.dto.DecideProfileReportRequest;
import ru.itmo.nemat.weezzy.moderation.report.dto.ProfileReportResponse;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminProfileReportController {
	private final ProfileReportService reportService;

	@GetMapping
	public ResponseEntity<PageResponse<ProfileReportResponse>> findByStatus(
			@RequestParam(defaultValue = "PENDING") ProfileReportStatus status,
			@PageableDefault(size = 20) Pageable pageable
	) {
		Page<ProfileReportResponse> reports = reportService
				.findByStatus(status, pageable)
				.map(ProfileReportResponse::from);
		return ResponseEntity.ok(PageResponse.from(reports));
	}

	@GetMapping("/{reportId}")
	public ResponseEntity<ProfileReportResponse> findById(
			@PathVariable UUID reportId
	) {
		return ResponseEntity.ok(ProfileReportResponse.from(
				reportService.findById(reportId)
		));
	}

	@PatchMapping("/{reportId}/review")
	public ResponseEntity<ProfileReportResponse> markReviewed(
			@PathVariable UUID reportId,
			@AuthenticationPrincipal JwtAuthenticatedUser adminUser
	) {
		return ResponseEntity.ok(ProfileReportResponse.from(
				reportService.markReviewed(reportId, adminUser.id())
		));
	}

	@PatchMapping("/{reportId}/decision")
	public ResponseEntity<ProfileReportResponse> decide(
			@PathVariable UUID reportId,
			@AuthenticationPrincipal JwtAuthenticatedUser adminUser,
			@Valid @RequestBody DecideProfileReportRequest request
	) {
		return ResponseEntity.ok(ProfileReportResponse.from(
				reportService.decide(
						reportId,
						adminUser.id(),
						request.status(),
						request.decision()
				)
		));
	}
}
