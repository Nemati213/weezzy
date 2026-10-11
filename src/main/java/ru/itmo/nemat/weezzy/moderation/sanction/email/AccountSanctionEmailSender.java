package ru.itmo.nemat.weezzy.moderation.sanction.email;

import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;

import java.time.LocalDateTime;

public interface AccountSanctionEmailSender {
    void sendSanctionCreated(
            String email,
            AccountSanctionType type,
            String reason,
            LocalDateTime expiresAt
    );

    void sendSanctionRevoked(
            String email,
            String revocationReason
    );
}