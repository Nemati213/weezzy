package ru.itmo.nemat.weezzy.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ObjectStorageProperties.class)
class S3StorageConfiguration {

	@Bean
	S3Client s3Client(ObjectStorageProperties properties) {
		return S3Client.builder()
				.endpointOverride(properties.endpoint())
				.region(Region.of(properties.region()))
				.credentialsProvider(credentialsProvider(properties))
				.httpClientBuilder(ApacheHttpClient.builder()
						.connectionTimeout(properties.connectTimeout())
						.socketTimeout(properties.socketTimeout()))
				.overrideConfiguration(ClientOverrideConfiguration.builder()
						.apiCallAttemptTimeout(properties.apiCallAttemptTimeout())
						.apiCallTimeout(properties.apiCallTimeout())
						.build())
				.serviceConfiguration(s3Configuration())
				.build();
	}

	@Bean
	S3Presigner s3Presigner(ObjectStorageProperties properties) {
		return S3Presigner.builder()
				.endpointOverride(properties.endpoint())
				.region(Region.of(properties.region()))
				.credentialsProvider(credentialsProvider(properties))
				.serviceConfiguration(s3Configuration())
				.build();
	}

	private StaticCredentialsProvider credentialsProvider(
			ObjectStorageProperties properties
	) {
		AwsBasicCredentials credentials = AwsBasicCredentials.create(
				properties.accessKey(),
				properties.secretKey()
		);
		return StaticCredentialsProvider.create(credentials);
	}

	private S3Configuration s3Configuration() {
		return S3Configuration.builder()
				.pathStyleAccessEnabled(true)
				.build();
	}
}
