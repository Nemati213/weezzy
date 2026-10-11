package ru.itmo.nemat.weezzy.moderation.sanction.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;

import java.time.LocalDateTime;

@Component
@Profile("local")
public class LocalAccountSanctionEmailSender implements AccountSanctionEmailSender {
	private static final Logger log = LoggerFactory.getLogger(
			LocalAccountSanctionEmailSender.class
	);

	@Override
	public void sendSanctionCreated(
			String email,
			AccountSanctionType type,
			String reason,
			LocalDateTime expiresAt
	) {
		log.info(
				"Local sanction email to {}: type={}, reason={}, expiresAt={}",
				email,
				type,
				reason,
				expiresAt
		);
	}

	@Override
	public void sendSanctionRevoked(String email, String revocationReason) {
		log.info(
				"Local sanction revocation email to {}: reason={}",
				email,
				revocationReason
		);
	}
}
