package com.dfwl.fleet.attachment.storage;

public record StoredFile(
        String storageKey,
        long fileSize,
        String sha256,
        boolean newlyCreated
) {
}
