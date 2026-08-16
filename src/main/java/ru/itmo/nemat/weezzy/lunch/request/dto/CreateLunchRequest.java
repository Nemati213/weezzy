package ru.itmo.nemat.weezzy.lunch.request.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.itmo.nemat.weezzy.lunch.request.LunchTimeOption;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;

import java.util.UUID;

public record CreateLunchRequest(
		@NotNull UUID locationId,
		@NotNull LunchTimeOption time,
		@NotNull LunchTopic topic,
		@Size(max = 255) String comment
) {
}
