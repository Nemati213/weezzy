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
		@NotNull Duration downloadUrlTtl
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
}
