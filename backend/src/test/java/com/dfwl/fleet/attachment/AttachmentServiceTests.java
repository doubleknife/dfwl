package com.dfwl.fleet.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dfwl.fleet.attachment.domain.FileAttachment;
import com.dfwl.fleet.attachment.repository.FileAttachmentRepository;
import com.dfwl.fleet.attachment.service.AttachmentService;
import com.dfwl.fleet.attachment.storage.StorageService;
import com.dfwl.fleet.attachment.storage.StoredFile;
import com.dfwl.fleet.security.AuthenticatedUser;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class AttachmentServiceTests {

    @Test
    void metadataSaveFailureDeletesNewlyStoredFile() {
        FailingRepository repository = new FailingRepository();
        RecordingStorage storage = new RecordingStorage();
        AttachmentService service = new AttachmentService(repository, storage);
        AuthenticatedUser user = new AuthenticatedUser(
                1L, "13800000001", 1L, "importer", "导入员", Set.of("import:preview", "import:history"));

        assertThatThrownBy(() -> service.upload(
                "IMPORT",
                0,
                "IMPORT_FILE",
                new MockMultipartFile("file", "routes.csv", "text/csv", "a,b\n1,2".getBytes()),
                user))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(storage.deletedKey).isEqualTo("stored/key.csv");
    }

    private static class FailingRepository extends FileAttachmentRepository {

        FailingRepository() {
            super((JdbcTemplate) null);
        }

        @Override
        public long create(String ownerType, long ownerId, String purpose, String storageKey,
                           String originalFilename, String contentType, long fileSize, String fileHash,
                           long uploadedBy) {
            throw new DataAccessResourceFailureException("boom");
        }

        @Override
        public boolean ownerExists(String ownerType, long ownerId) {
            return true;
        }

        @Override
        public Optional<FileAttachment> find(long id) {
            return Optional.of(new FileAttachment(
                    id, "IMPORT", 0, "IMPORT_FILE", "stored/key.csv", "routes.csv",
                    "text/csv", 7, "hash", 1, LocalDateTime.now()));
        }
    }

    private static class RecordingStorage implements StorageService {

        private String deletedKey;

        @Override
        public StoredFile store(byte[] content, String originalFilename, String contentType) {
            return new StoredFile("stored/key.csv", content.length, "hash", true);
        }

        @Override
        public Resource load(String storageKey) {
            return new ByteArrayResource(new byte[0]);
        }

        @Override
        public void delete(String storageKey) {
            this.deletedKey = storageKey;
        }
    }
}
