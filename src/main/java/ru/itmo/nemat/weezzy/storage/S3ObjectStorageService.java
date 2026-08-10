package ru.itmo.nemat.weezzy.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itmo.nemat.weezzy.storage.dto.PresignedDownload;
import ru.itmo.nemat.weezzy.storage.dto.PresignedUpload;
import ru.itmo.nemat.weezzy.storage.dto.StoredObjectMetadata;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class S3ObjectStorageService implements ObjectStorageService{
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ObjectStorageProperties properties;

    @Override
	public PresignedUpload createUpload(String objectKey, String contentType, long sizeBytes) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return new PresignedUpload(
                presignedRequest.url().toString(),
                presignedRequest.expiration().atZone(ZoneOffset.UTC).toLocalDateTime()
        );

	}

	@Override
	public PresignedDownload createDownload(String objectKey) {
		GetObjectRequest getObjectRequest = GetObjectRequest.builder()
				.bucket(properties.bucket())
				.key(objectKey)
				.build();
		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
				.signatureDuration(properties.downloadUrlTtl())
				.getObjectRequest(getObjectRequest)
				.build();
		PresignedGetObjectRequest presignedRequest =
				s3Presigner.presignGetObject(presignRequest);

		return new PresignedDownload(
				presignedRequest.url().toString(),
				presignedRequest.expiration()
						.atZone(ZoneOffset.UTC)
						.toLocalDateTime()
		);
	}

    @Override
    public Optional<StoredObjectMetadata> getMetadata(String objectKey) {
        try {

            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build();

            HeadObjectResponse response = s3Client.headObject(request);

            return Optional.of(new StoredObjectMetadata(
                    response.contentType(),
                    response.contentLength()));
		} catch (S3Exception e) {
			if (e.statusCode() == 404) {
				return Optional.empty();
			}
			throw e;
		}
    }

    @Override
    public void deleteObject(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();

        s3Client.deleteObject(request);
    }

}
