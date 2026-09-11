package com.dfwl.fleet.attachment.domain;

import java.time.LocalDateTime;

public record FileAttachment(
        long id,
        String ownerType,
        long ownerId,
        String purpose,
        String storageKey,
        String originalFilename,
        String contentType,
        long fileSize,
        String fileHash,
        long uploadedBy,
        LocalDateTime uploadedAt
) {
}
