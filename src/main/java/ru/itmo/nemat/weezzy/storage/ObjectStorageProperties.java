package ru.itmo.nemat.weezzy.storage;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public record ObjectStorageProperties(
		@NotNull URI endpoint,
		@NotBlank String region,
		@NotBlank String accessKey,
		@NotBlank String secretKey,
		@NotBlank String bucket,
		@NotNull Duration uploadUrlTtl,
		@NotNull Duration downloadUrlTtl,
		@NotNull Duration connectTimeout,
		@NotNull Duration socketTimeout,
		@NotNull Duration apiCallAttemptTimeout,
		@NotNull Duration apiCallTimeout
) {
	@AssertTrue(message = "upload-url-ttl must be positive")
	public boolean isUploadUrlTtlPositive() {
		return uploadUrlTtl != null
				&& !uploadUrlTtl.isZero()
				&& !uploadUrlTtl.isNegative();
	}

	@AssertTrue(message = "download-url-ttl must be positive")
	public boolean isDownloadUrlTtlPositive() {
		return downloadUrlTtl != null
				&& !downloadUrlTtl.isZero()
				&& !downloadUrlTtl.isNegative();
	}

	@AssertTrue(message = "S3 timeouts must be positive and attempt timeout "
			+ "must be shorter than total API call timeout")
	public boolean isTimeoutConfigurationValid() {
		return isPositive(connectTimeout)
				&& isPositive(socketTimeout)
				&& isPositive(apiCallAttemptTimeout)
				&& isPositive(apiCallTimeout)
				&& apiCallAttemptTimeout.compareTo(apiCallTimeout) < 0;
	}

	private boolean isPositive(Duration duration) {
		return duration != null && !duration.isZero() && !duration.isNegative();
	}
}
