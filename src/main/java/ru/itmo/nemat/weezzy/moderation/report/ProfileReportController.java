package ru.itmo.nemat.weezzy.moderation.report;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.moderation.report.dto.CreateProfileReportRequest;
import ru.itmo.nemat.weezzy.moderation.report.dto.ProfileReportResponse;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ProfileReportController {
	private final ProfileReportService reportService;
	private final ProfileService profileService;

	@PostMapping("/{targetProfileId}")
	public ResponseEntity<ProfileReportResponse> create(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID targetProfileId,
			@Valid @RequestBody CreateProfileReportRequest request
	) {
		UUID reporterProfileId = profileService
				.findByUserId(authenticatedUser.id())
				.getId();
		ProfileReport report = reportService.create(
				reporterProfileId,
				targetProfileId,
				request
		);

		return ResponseEntity
				.created(URI.create("/api/reports/" + report.getId()))
				.body(ProfileReportResponse.from(report));
	}
}
