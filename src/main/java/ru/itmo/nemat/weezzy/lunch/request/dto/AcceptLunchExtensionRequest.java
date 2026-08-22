package ru.itmo.nemat.weezzy.lunch.request.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcceptLunchExtensionRequest(
		@NotNull UUID offerId
) {
}
