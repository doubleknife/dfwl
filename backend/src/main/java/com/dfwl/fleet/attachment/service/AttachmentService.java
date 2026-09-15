package com.dfwl.fleet.attachment.service;

import com.dfwl.fleet.attachment.api.AttachmentResponse;
import com.dfwl.fleet.attachment.domain.AttachmentPurpose;
import com.dfwl.fleet.attachment.domain.FileAttachment;
import com.dfwl.fleet.attachment.repository.FileAttachmentRepository;
import com.dfwl.fleet.attachment.storage.StorageService;
import com.dfwl.fleet.attachment.storage.StoredFile;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.security.AuthenticatedUser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {

    private static final long MAX_FILE_SIZE = 30L * 1024L * 1024L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");
    private static final Set<String> APPROVAL_TYPES = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");
    private static final Set<String> IMPORT_TYPES = Set.of(
            "text/csv",
            "application/csv",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final FileAttachmentRepository repository;
    private final StorageService storageService;

    public AttachmentService(FileAttachmentRepository repository, StorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(String ownerType, Long ownerId, String purpose, AuthenticatedUser user) {
        String normalizedPurpose = purpose == null ? null : normalizePurpose(purpose).name();
        String normalizedOwnerType = ownerType == null ? null : normalizeOwnerType(ownerType);
        return repository.list(normalizedOwnerType, ownerId, normalizedPurpose).stream()
                .filter(attachment -> canRead(attachment, user))
                .map(AttachmentResponse::from)
                .toList();
    }

    @Transactional
    public AttachmentResponse upload(String ownerType, long ownerId, String purpose, MultipartFile file, AuthenticatedUser user) {
        String normalizedOwnerType = normalizeOwnerType(ownerType);
        AttachmentPurpose normalizedPurpose = normalizePurpose(purpose);
        validateUpload(normalizedOwnerType, ownerId, normalizedPurpose, file, user);

        String originalFilename = cleanFilename(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        try {
            byte[] bytes = file.getBytes();
            StoredFile stored = storageService.store(bytes, originalFilename, contentType);
            FileAttachment duplicate = repository.findDuplicate(
                    normalizedOwnerType, ownerId, normalizedPurpose.name(), stored.sha256(), originalFilename, stored.fileSize())
                    .orElse(null);
            if (duplicate != null) {
                return AttachmentResponse.from(duplicate);
            }
            try {
                long id = repository.create(
                        normalizedOwnerType,
                        ownerId,
                        normalizedPurpose.name(),
                        stored.storageKey(),
                        originalFilename,
                        contentType,
                        stored.fileSize(),
                        stored.sha256(),
                        user.id());
                return AttachmentResponse.from(repository.find(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_001)));
            } catch (DataAccessException ex) {
                if (stored.newlyCreated()) {
                    storageService.delete(stored.storageKey());
                }
                throw ex;
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Transactional(readOnly = true)
    public void requireTemporaryApprovalAttachments(long userId, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        requireTemporaryApprovalAttachments(userId, attachmentIds, "APPROVAL_APPLICATION");
    }

    @Transactional(readOnly = true)
    public void requireTemporaryApprovalActionAttachments(long userId, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        requireTemporaryApprovalAttachments(userId, attachmentIds, "APPROVAL_ACTION");
    }

    private void requireTemporaryApprovalAttachments(long userId, List<Long> attachmentIds, String purpose) {
        List<FileAttachment> attachments = repository.findByIds(attachmentIds);
        if (attachments.size() != Set.copyOf(attachmentIds).size()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_001);
        }
        boolean invalid = attachments.stream().anyMatch(attachment ->
                !"APPROVAL_UPLOAD".equals(attachment.ownerType())
                        || attachment.ownerId() != userId
                        || !purpose.equals(attachment.purpose())
                        || attachment.uploadedBy() != userId);
        if (invalid) {
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        }
    }

    @Transactional
    public void bindApprovalApplicationAttachments(long userId, List<Long> attachmentIds,
                                                   long approvalInstanceId, long submissionVersionId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        requireTemporaryApprovalAttachments(userId, attachmentIds);
        int updated = repository.bindTemporaryApprovalAttachments(userId, attachmentIds, approvalInstanceId, submissionVersionId);
        if (updated != Set.copyOf(attachmentIds).size()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        }
    }

    @Transactional
    public void bindApprovalActionAttachments(long userId, List<Long> attachmentIds, long actionId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        requireTemporaryApprovalActionAttachments(userId, attachmentIds);
        int updated = repository.bindTemporaryApprovalActionAttachments(userId, attachmentIds, actionId);
        if (updated != Set.copyOf(attachmentIds).size()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        }
    }

    @Transactional(readOnly = true)
    public FileDownload download(long id, AuthenticatedUser user) {
        FileAttachment attachment = repository.find(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_001));
        requireRead(attachment, user);
        Resource resource = storageService.load(attachment.storageKey());
        if (!resource.exists() || !resource.isReadable()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_001);
        }
        return new FileDownload(attachment, resource);
    }

    public void rejectOrdinaryDelete(long id, AuthenticatedUser user) {
        FileAttachment attachment = repository.find(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_001));
        if (attachment.purpose().startsWith("APPROVAL_")) {
            throw new BusinessException(ErrorCode.ATTACHMENT_005);
        }
        throw new BusinessException(ErrorCode.ATTACHMENT_005);
    }

    private void validateUpload(String ownerType, long ownerId, AttachmentPurpose purpose, MultipartFile file, AuthenticatedUser user) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_002);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.ATTACHMENT_002);
        }
        requireUploadPermission(purpose, user);
        if (!ownerIdAllowedForPurpose(ownerId, purpose)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        }
        if (!repository.ownerExists(ownerType, ownerId)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        }
        if ("APPROVAL_UPLOAD".equals(ownerType) && ownerId != user.id()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!allowedTypes(purpose).contains(contentType)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_003);
        }
    }

    private void requireUploadPermission(AttachmentPurpose purpose, AuthenticatedUser user) {
        boolean allowed = switch (purpose) {
            case TIRE_OCR -> has(user, "tire:request");
            case WEIGHT_ADJUST -> has(user, "route:weight:adjust");
            case APPROVAL_APPLICATION -> has(user, "approval:create");
            case APPROVAL_ACTION -> hasAny(user, "approval:process", "approval:return");
            case IMPORT_FILE -> hasAny(user, "import:preview", "import:history");
        };
        if (!allowed) {
            throw new AccessDeniedException(ErrorCode.AUTH_003.defaultMessage());
        }
    }

    private boolean canRead(FileAttachment attachment, AuthenticatedUser user) {
        try {
            requireRead(attachment, user);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    private void requireRead(FileAttachment attachment, AuthenticatedUser user) {
        AttachmentPurpose purpose = normalizePurpose(attachment.purpose());
        boolean allowed = switch (purpose) {
            case TIRE_OCR -> hasAny(user, "tire:request", "tire:approval:view");
            case WEIGHT_ADJUST -> hasAny(user, "route:list", "route:weight:adjust");
            case APPROVAL_APPLICATION -> hasAny(user, "approval:history:view", "approval:create", "approval:process", "approval:return");
            case APPROVAL_ACTION -> hasAny(user, "approval:history:view", "approval:process", "approval:return");
            case IMPORT_FILE -> has(user, "import:history");
        };
        if (!allowed) {
            throw new AccessDeniedException(ErrorCode.AUTH_003.defaultMessage());
        }
        if (!ownerIdAllowedForPurpose(attachment.ownerId(), purpose)
                || !repository.ownerExists(attachment.ownerType(), attachment.ownerId())) {
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        }
        if ("APPROVAL_UPLOAD".equals(attachment.ownerType()) && attachment.ownerId() != user.id()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        }
    }

    private Set<String> allowedTypes(AttachmentPurpose purpose) {
        return switch (purpose) {
            case TIRE_OCR, WEIGHT_ADJUST -> IMAGE_TYPES;
            case APPROVAL_APPLICATION, APPROVAL_ACTION -> APPROVAL_TYPES;
            case IMPORT_FILE -> IMPORT_TYPES;
        };
    }

    private boolean ownerIdAllowedForPurpose(long ownerId, AttachmentPurpose purpose) {
        if (ownerId != 0) {
            return true;
        }
        return switch (purpose) {
            case TIRE_OCR, APPROVAL_APPLICATION, IMPORT_FILE -> true;
            case WEIGHT_ADJUST, APPROVAL_ACTION -> false;
        };
    }

    private boolean has(AuthenticatedUser user, String permission) {
        return user.permissions().contains(permission);
    }

    private boolean hasAny(AuthenticatedUser user, String... permissions) {
        for (String permission : permissions) {
            if (has(user, permission)) {
                return true;
            }
        }
        return false;
    }

    private AttachmentPurpose normalizePurpose(String purpose) {
        return AttachmentPurpose.parse(purpose)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_002));
    }

    private String normalizeOwnerType(String ownerType) {
        if (!StringUtils.hasText(ownerType) || ownerType.length() > 64) {
            throw new BusinessException(ErrorCode.ATTACHMENT_002);
        }
        return ownerType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType) || contentType.length() > 128) {
            throw new BusinessException(ErrorCode.ATTACHMENT_003);
        }
        return contentType.toLowerCase(Locale.ROOT);
    }

    private String cleanFilename(String originalFilename) {
        String filename = StringUtils.cleanPath(StringUtils.hasText(originalFilename) ? originalFilename : "file");
        if (filename.contains("..") || filename.length() > 255) {
            throw new BusinessException(ErrorCode.ATTACHMENT_002);
        }
        return filename;
    }

    public record FileDownload(FileAttachment attachment, Resource resource) {
    }
}
