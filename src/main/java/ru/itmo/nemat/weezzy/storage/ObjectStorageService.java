package ru.itmo.nemat.weezzy.storage;

import ru.itmo.nemat.weezzy.storage.dto.PresignedUpload;
import ru.itmo.nemat.weezzy.storage.dto.PresignedDownload;
import ru.itmo.nemat.weezzy.storage.dto.StoredObjectMetadata;

import java.util.Optional;

public interface ObjectStorageService {
	PresignedUpload createUpload(
            String objectKey,
            String contentType,
            long sizeBytes
	);

	PresignedDownload createDownload(String objectKey);

    Optional<StoredObjectMetadata> getMetadata(String objectKey);

    void deleteObject(String objectKey);
}
