package com.dfwl.fleet.attachment;

import static com.dfwl.fleet.security.TestFunctionGrants.grants;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class AttachmentServiceTests {

    @Test
    void metadataSaveFailureDeletesNewlyStoredFile() {
        FailingRepository repository = new FailingRepository();
        RecordingStorage storage = new RecordingStorage();
        AttachmentService service = new AttachmentService(repository, storage);
        AuthenticatedUser user = new AuthenticatedUser(
                1L, "13800000001", 1L, "ADMIN", "管理员", grants("BUTTON_ROUTES_WEIGHT_ADJUST"));

        assertThatThrownBy(() -> service.upload(
                "TASK",
                1,
                "WEIGHT_ADJUST",
                new MockMultipartFile("file", "evidence.png", "image/png", new byte[] {1, 2, 3}),
                user))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(storage.deletedKey).isEqualTo("stored/key.csv");
    }

    @Test
    void importFileReadIsScopedToTheActualBusinessType() {
        FailingRepository repository = new FailingRepository();
        RecordingStorage storage = new RecordingStorage();
        AttachmentService service = new AttachmentService(repository, storage);
        AuthenticatedUser taskViewer = new AuthenticatedUser(2L, "13800000002", 1L, "ADMIN", "任务查看员",
                grants("PAGE_TASK_IMPORTS"));
        AuthenticatedUser tireViewer = new AuthenticatedUser(3L, "13800000003", 1L, "ADMIN", "轮胎查看员",
                grants("BUTTON_TIRE_IMPORTS_SUBMIT"));
        repository.importType = "TASK";
        assertThat(service.download(10, taskViewer).resource().exists()).isTrue();
        assertThatThrownBy(() -> service.download(10, tireViewer)).isInstanceOf(AccessDeniedException.class);
        repository.importType = "TIRE";
        assertThat(service.download(10, tireViewer).resource().exists()).isTrue();
        assertThatThrownBy(() -> service.download(10, taskViewer)).isInstanceOf(AccessDeniedException.class);
    }

    private static class FailingRepository extends FileAttachmentRepository {

        String importType;

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
                    id, "IMPORT", 7, "IMPORT_FILE", "stored/key.csv", "routes.csv",
                    "text/csv", 7, "hash", 1, LocalDateTime.now()));
        }

        @Override
        public String importFileBusinessType(long importId, long attachmentId) { return importType; }
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
