package ru.itmo.nemat.weezzy.moderation.sanction;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.moderation.report.ProfileReport;
import ru.itmo.nemat.weezzy.moderation.report.ProfileReportService;
import ru.itmo.nemat.weezzy.moderation.report.ProfileReportStatus;
import ru.itmo.nemat.weezzy.moderation.sanction.dto.CreateAccountSanctionRequest;
import ru.itmo.nemat.weezzy.moderation.sanction.dto.RevokeAccountSanctionRequest;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.security.session.AuthSessionService;
import ru.itmo.nemat.weezzy.security.revocation.AccessTokenRevocationReason;
import ru.itmo.nemat.weezzy.security.revocation.AccessTokenRevocationService;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserNotFoundException;
import ru.itmo.nemat.weezzy.user.UserRepository;
import ru.itmo.nemat.weezzy.user.UserRole;
import ru.itmo.nemat.weezzy.user.UserService;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountSanctionService {
	private final AccountSanctionRepository sanctionRepository;
	private final UserRepository userRepository;
	private final UserService userService;
	private final ProfileService profileService;
	private final ProfileReportService reportService;
	private final AuthSessionService authSessionService;
	private final AccessTokenRevocationService accessTokenRevocationService;

	@Transactional
	public AccountSanction create(
			UUID targetUserId,
			UUID moderatorUserId,
			CreateAccountSanctionRequest request
	) {
		if (targetUserId.equals(moderatorUserId)) {
			throw new SelfAccountSanctionException(moderatorUserId);
		}

		LocalDateTime now = LocalDateTime.now();
		validateExpiration(request, now);
		User moderator = userService.findAdminById(moderatorUserId);
		User targetUser = userRepository.findByIdForUpdate(targetUserId)
				.orElseThrow(() -> new UserNotFoundException(targetUserId));
		if (targetUser.getRole() == UserRole.ADMIN) {
			throw new AdminAccountSanctionNotAllowedException(targetUserId);
		}

		expireTemporarySanctions(now);
		ensureNoActiveSanction(targetUserId);

		Optional<Profile> targetProfile = profileService.findOptionalByUserId(targetUserId);
		ProfileReport sourceReport = findAndValidateSourceReport(
				request.sourceReportId(),
				targetProfile,
				targetUserId
		);

		AccountSanction saved = createSanction(
				targetUserId,
				targetProfile,
				sourceReport,
				request,
				moderator
		);

		authSessionService.revokeAllForSanction(targetUserId);
		accessTokenRevocationService.revokeAllIssuedTokens(
				targetUserId,
				AccessTokenRevocationReason.ACCOUNT_SANCTION
		);

		return saved;
	}

	@Transactional
	public AccountSanction findById(UUID sanctionId) {
		expireTemporarySanctions(LocalDateTime.now());
		return sanctionRepository.findById(sanctionId)
				.orElseThrow(() -> new AccountSanctionNotFoundException(sanctionId));
	}

	@Transactional
	public Page<AccountSanction> findByStatus(
			AccountSanctionStatus status,
			Pageable pageable
	) {
		expireTemporarySanctions(LocalDateTime.now());
		return sanctionRepository.findByStatusOrderByCreatedAtDescIdDesc(
				status,
				pageable
		);
	}

	@Transactional
	public Page<AccountSanction> findByTargetUserId(
			UUID targetUserId,
			Pageable pageable
	) {
		expireTemporarySanctions(LocalDateTime.now());
		return sanctionRepository.findByTargetUserIdOrderByCreatedAtDescIdDesc(
				targetUserId,
				pageable
		);
	}

	@Transactional(readOnly = true)
	public Optional<AccountSanction> findEffectiveByTargetUserId(UUID targetUserId) {
		return sanctionRepository.findEffectiveByTargetUserId(
				targetUserId,
				LocalDateTime.now()
		);
	}

	@Transactional(noRollbackFor = AccountSanctionStatusConflictException.class)
	public AccountSanction revoke(
			UUID sanctionId,
			UUID moderatorUserId,
			RevokeAccountSanctionRequest request
	) {
		User moderator = userService.findAdminById(moderatorUserId);
		AccountSanction sanction = sanctionRepository.findByIdForUpdate(sanctionId)
				.orElseThrow(() -> new AccountSanctionNotFoundException(sanctionId));
		expireIfNecessary(sanction, LocalDateTime.now());
		if (sanction.getStatus() != AccountSanctionStatus.ACTIVE) {
			throw new AccountSanctionStatusConflictException(
					sanctionId,
					sanction.getStatus()
			);
		}

		sanction.setStatus(AccountSanctionStatus.REVOKED);
		sanction.setRevokedAt(LocalDateTime.now());
		sanction.setRevokedByUserId(moderator.getId());
		sanction.setRevocationReason(request.reason().trim());
		return sanctionRepository.save(sanction);
	}

	private void validateExpiration(
			CreateAccountSanctionRequest request,
			LocalDateTime now
	) {
		if (request.type() == AccountSanctionType.TEMPORARY_SUSPENSION) {
			if (request.expiresAt() == null || !request.expiresAt().isAfter(now)) {
				throw new InvalidAccountSanctionExpirationException(
						"Temporary suspension expiration must be in the future"
				);
			}
			return;
		}

		if (request.expiresAt() != null) {
			throw new InvalidAccountSanctionExpirationException(
					"Permanent ban must not have an expiration"
			);
		}
	}

	private void ensureNoActiveSanction(UUID targetUserId) {
		if (sanctionRepository.findByTargetUserIdAndStatusForUpdate(
				targetUserId,
				AccountSanctionStatus.ACTIVE
		).isPresent()) {
			throw new DuplicateActiveAccountSanctionException(targetUserId);
		}
	}

	private ProfileReport findAndValidateSourceReport(
			UUID sourceReportId,
			Optional<Profile> targetProfile,
			UUID targetUserId
	) {
		if (sourceReportId == null) {
			return null;
		}

		ProfileReport report = reportService.findById(sourceReportId);
		if (report.getStatus() != ProfileReportStatus.RESOLVED) {
			throw new ProfileReportNotResolvedForSanctionException(sourceReportId);
		}
		if (targetProfile.isEmpty()
				|| !report.getTargetProfile().getId().equals(targetProfile.get().getId())) {
			throw new AccountSanctionReportMismatchException(
					sourceReportId,
					targetUserId
			);
		}
		return report;
	}

	private void expireTemporarySanctions(LocalDateTime now) {
		sanctionRepository.expireAllTemporarySanctions(now);
	}

	private void expireIfNecessary(AccountSanction sanction, LocalDateTime now) {
		if (sanction.getStatus() == AccountSanctionStatus.ACTIVE
				&& sanction.getType() == AccountSanctionType.TEMPORARY_SUSPENSION
				&& !sanction.getExpiresAt().isAfter(now)) {
			sanction.setStatus(AccountSanctionStatus.EXPIRED);
			sanctionRepository.saveAndFlush(sanction);
		}
	}

	private AccountSanction createSanction(
			UUID userId,
			Optional<Profile> profile,
			ProfileReport report,
			CreateAccountSanctionRequest request,
			User moderator
	) {
		AccountSanction sanction = new AccountSanction();
		sanction.setTargetUserId(userId);
		sanction.setTargetProfileId(profile.map(Profile::getId).orElse(null));
		sanction.setSourceReportId(report == null ? null : report.getId());
		sanction.setType(request.type());
		sanction.setStatus(AccountSanctionStatus.ACTIVE);
		sanction.setReason(request.reason().trim());
		sanction.setExpiresAt(request.expiresAt());
		sanction.setCreatedByUserId(moderator.getId());

		return sanctionRepository.save(sanction);
	}
}
