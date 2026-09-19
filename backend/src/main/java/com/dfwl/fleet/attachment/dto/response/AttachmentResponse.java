package com.dfwl.fleet.attachment.dto.response;

import com.dfwl.fleet.attachment.domain.FileAttachment;
import java.time.LocalDateTime;

public record AttachmentResponse(
        long id,
        String ownerType,
        long ownerId,
        String purpose,
        String originalFilename,
        String contentType,
        long fileSize,
        String fileHash,
        String storageKey,
        long uploadedBy,
        LocalDateTime uploadedAt
) {
    public static AttachmentResponse from(FileAttachment attachment) {
        return new AttachmentResponse(
                attachment.id(),
                attachment.ownerType(),
                attachment.ownerId(),
                attachment.purpose(),
                attachment.originalFilename(),
                attachment.contentType(),
                attachment.fileSize(),
                attachment.fileHash(),
                attachment.storageKey(),
                attachment.uploadedBy(),
                attachment.uploadedAt());
    }
}
