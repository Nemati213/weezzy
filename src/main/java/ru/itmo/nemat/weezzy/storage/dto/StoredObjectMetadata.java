package ru.itmo.nemat.weezzy.storage.dto;

public record StoredObjectMetadata(
        String contentType,
        long sizeBytes
) {
}
