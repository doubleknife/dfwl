package com.dfwl.fleet.tire.service;

import com.dfwl.fleet.attachment.domain.AttachmentFile;
import com.dfwl.fleet.attachment.service.AttachmentService;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.tire.dto.request.OcrConfirmRequest;
import com.dfwl.fleet.tire.dto.request.OcrTireNumberRequest;
import com.dfwl.fleet.tire.dto.response.OcrRecordResponse;
import com.dfwl.fleet.tire.ocr.OcrProperties;
import com.dfwl.fleet.tire.ocr.OcrProvider;
import com.dfwl.fleet.tire.ocr.OcrProviderException;
import com.dfwl.fleet.tire.ocr.OcrProviderResult;
import com.dfwl.fleet.tire.ocr.TireNumberCandidateExtractor;
import com.dfwl.fleet.tire.repository.TireRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

/** Photo OCR only; this service does not create or mutate a tire request. */
@Service
public class TireService {
    private static final long MAX_IMAGE_SIZE = 10L * 1024L * 1024L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");
    private final TireRepository repository;
    private final AttachmentService attachments;
    private final OcrProvider provider;
    private final TireNumberCandidateExtractor candidates;
    private final OcrProperties properties;
    public TireService(TireRepository repository, AttachmentService attachments, OcrProvider provider,
            TireNumberCandidateExtractor candidates, OcrProperties properties) {
        this.repository = repository; this.attachments = attachments; this.provider = provider;
        this.candidates = candidates; this.properties = properties;
    }
    @Transactional
    public OcrRecordResponse recognize(OcrTireNumberRequest request, AuthenticatedUser user) {
        if (request == null || request.attachmentId() == null) throw new BusinessException(ErrorCode.ATTACHMENT_004);
        attachments.requireTireOcrEvidence(user.id(), List.of(request.attachmentId()));
        AttachmentFile file = attachments.download(request.attachmentId(), user);
        if (!IMAGE_TYPES.contains(file.attachment().contentType()) || file.attachment().fileSize() <= 0
                || file.attachment().fileSize() > MAX_IMAGE_SIZE) throw new BusinessException(ErrorCode.ATTACHMENT_003);
        byte[] bytes;
        try { bytes = StreamUtils.copyToByteArray(file.resource().getInputStream()); }
        catch (IOException ex) { throw new UncheckedIOException(ex); }
        long id;
        try {
            OcrProviderResult result = provider.recognize(Base64.getEncoder().encodeToString(bytes),
                    file.attachment().contentType());
            List<OcrRecordResponse.Candidate> found = candidates.extract(result.detections()).stream()
                    .map(item -> new OcrRecordResponse.Candidate(item.candidate(), item.confidence(),
                            item.sourceText(), item.inventoryMatched())).toList();
            id = repository.createOcr(request.attachmentId(), result.provider(), result.requestId(),
                    result.rawResultJson(), candidates.joinedText(result.detections()),
                    found.isEmpty() ? null : found.getFirst().candidate(), found,
                    found.isEmpty() ? "NO_CANDIDATE" : "SUCCESS", null, null);
        } catch (OcrProviderException ex) {
            id = repository.createOcr(request.attachmentId(), properties.getProvider(), null, "{}",
                    null, null, List.of(), "FAILED", ex.errorCode(), ex.getMessage());
        }
        return find(id);
    }
    @Transactional
    public OcrRecordResponse confirm(long id, OcrConfirmRequest request, long operatorId) {
        find(id);
        if (request == null || request.confirmedText() == null || request.confirmedText().isBlank()
                || request.confirmedText().trim().length() > 128)
            throw new BusinessException(ErrorCode.TIRE_004);
        repository.confirmOcr(id, request.confirmedText().trim(), operatorId);
        return find(id);
    }
    private OcrRecordResponse find(long id) {
        return repository.findOcr(id).orElseThrow(() -> new BusinessException(ErrorCode.TIRE_003));
    }
}
