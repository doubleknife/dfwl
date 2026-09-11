package com.dfwl.fleet.attachment.storage;

import java.io.IOException;
import org.springframework.core.io.Resource;

public interface StorageService {

    StoredFile store(byte[] content, String originalFilename, String contentType) throws IOException;

    Resource load(String storageKey);

    void delete(String storageKey);
}
