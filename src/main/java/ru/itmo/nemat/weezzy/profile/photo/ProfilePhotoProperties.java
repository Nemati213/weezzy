package ru.itmo.nemat.weezzy.profile.photo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "app.profile-photo")
public record ProfilePhotoProperties(
		@Positive long maxFileSize,
		@Min(1) int maxPhotos,
		@NotNull Duration pendingTtl,
		@NotEmpty Set<@NotBlank String> allowedContentTypes
) {
	@AssertTrue(message = "pending-ttl must be positive")
	public boolean isPendingTtlPositive() {
		return pendingTtl != null
				&& !pendingTtl.isZero()
				&& !pendingTtl.isNegative();
	}
}
