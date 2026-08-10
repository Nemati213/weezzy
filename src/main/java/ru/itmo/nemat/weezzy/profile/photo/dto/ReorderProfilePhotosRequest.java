package ru.itmo.nemat.weezzy.profile.photo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReorderProfilePhotosRequest(
		@NotEmpty List<@NotNull UUID> photoIds
) {
}
