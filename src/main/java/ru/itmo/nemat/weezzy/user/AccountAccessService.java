package ru.itmo.nemat.weezzy.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanction;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionRepository;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountAccessService {
	private final AccountSanctionRepository sanctionRepository;

	@Transactional(readOnly = true)
	public Optional<AccountSanction> findRestriction(UUID userId) {
		return sanctionRepository.findEffectiveByTargetUserId(
				userId,
				LocalDateTime.now()
		);
	}

	@Transactional(readOnly = true)
	public void ensureAccessAllowed(UUID userId) {
		findRestriction(userId).ifPresent(this::throwRestriction);
	}

	@Transactional(readOnly = true)
	public boolean isAccessAllowed(UUID userId) {
		return findRestriction(userId).isEmpty();
	}

	@Transactional(readOnly = true)
	public Set<UUID> findRestrictedUserIds(Collection<UUID> userIds) {
		if (userIds.isEmpty()) {
			return Set.of();
		}
		return sanctionRepository.findEffectiveTargetUserIds(
				userIds,
				LocalDateTime.now()
		);
	}

	private void throwRestriction(AccountSanction sanction) {
		if (sanction.getType() == AccountSanctionType.PERMANENT_BAN) {
			throw new AccountPermanentlyBannedException(sanction.getReason());
		}

		throw new AccountTemporarilySuspendedException(
				sanction.getReason(),
				sanction.getExpiresAt()
		);
	}
}
