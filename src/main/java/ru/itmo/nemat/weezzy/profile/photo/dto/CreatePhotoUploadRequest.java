package ru.itmo.nemat.weezzy.profile.photo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreatePhotoUploadRequest(
        @NotBlank(message = "Тип файла не может быть пустым")
        String contentType,

        @Positive(message = "Размер файла должен быть больше нуля")
        long sizeBytes

) {
}
