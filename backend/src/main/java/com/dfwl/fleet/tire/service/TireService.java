package com.dfwl.fleet.tire.service;

import com.dfwl.fleet.common.api.PageResponse;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.attachment.service.AttachmentService;
import com.dfwl.fleet.attachment.domain.AttachmentFile;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.tire.dto.request.OcrConfirmRequest;
import com.dfwl.fleet.tire.dto.response.OcrRecordResponse;
import com.dfwl.fleet.tire.dto.request.OcrTireNumberRequest;
import com.dfwl.fleet.tire.dto.request.TireRequestCreateRequest;
import com.dfwl.fleet.tire.dto.response.TireRequestResponse;
import com.dfwl.fleet.tire.dto.response.TireResponse;
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
import org.springframework.util.StreamUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TireService {

    private static final long MAX_OCR_IMAGE_SIZE = 10L * 1024L * 1024L;
    private static final Set<String> OCR_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");

    private final TireRepository repository;
    private final AttachmentService attachmentService;
    private final OcrProvider ocrProvider;
    private final TireNumberCandidateExtractor candidateExtractor;
    private final OcrProperties ocrProperties;

    public TireService(TireRepository repository, AttachmentService attachmentService, OcrProvider ocrProvider,
                       TireNumberCandidateExtractor candidateExtractor, OcrProperties ocrProperties) {
        this.repository = repository;
        this.attachmentService = attachmentService;
        this.ocrProvider = ocrProvider;
        this.candidateExtractor = candidateExtractor;
        this.ocrProperties = ocrProperties;
    }

    public PageResponse<TireResponse> list(int pageNo, int pageSize) {
        return repository.list(pageNo, Math.min(Math.max(pageSize, 1), 200));
    }

    public TireRequestResponse findRequest(long id) {
        return repository.findRequest(id).orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
    }

    @Transactional
    public OcrRecordResponse recognize(OcrTireNumberRequest request, AuthenticatedUser user) {
        AttachmentFile download = attachmentService.download(request.attachmentId(), user);
        if (!"TIRE_OCR".equals(download.attachment().purpose())
                || download.attachment().uploadedBy() != user.id()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_004);
        }
        if (!OCR_IMAGE_TYPES.contains(download.attachment().contentType())
                || download.attachment().fileSize() <= 0
                || download.attachment().fileSize() > MAX_OCR_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.ATTACHMENT_003);
        }
        byte[] imageBytes;
        try {
            imageBytes = StreamUtils.copyToByteArray(download.resource().getInputStream());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        long id;
        try {
            OcrProviderResult result = ocrProvider.recognize(Base64.getEncoder().encodeToString(imageBytes),
                    download.attachment().contentType());
            List<OcrRecordResponse.Candidate> candidates = toResponseCandidates(candidateExtractor.extract(result.detections()));
            String candidateText = candidates.isEmpty() ? null : candidates.getFirst().candidate();
            id = repository.createOcr(
                    request.attachmentId(),
                    result.provider(),
                    result.requestId(),
                    result.rawResultJson(),
                    candidateExtractor.joinedText(result.detections()),
                    candidateText,
                    candidates,
                    candidates.isEmpty() ? "NO_CANDIDATE" : "SUCCESS",
                    null,
                    null);
        } catch (OcrProviderException ex) {
            id = repository.createOcr(
                    request.attachmentId(),
                    configuredProviderName(),
                    null,
                    "{}",
                    null,
                    null,
                    List.of(),
                    "FAILED",
                    ex.errorCode(),
                    ex.getMessage());
        }
        return findOcr(id);
    }

    @Transactional
    public OcrRecordResponse confirm(long id, OcrConfirmRequest request, long operatorId) {
        findOcr(id);
        repository.confirmOcr(id, request.confirmedText(), operatorId);
        return findOcr(id);
    }

    @Transactional
    public TireRequestResponse createRequest(TireRequestCreateRequest request) {
        if (!repository.driverExists(request.driverId()) || !repository.vehicleExists(request.vehicleId())) {
            throw new BusinessException(ErrorCode.DATA_001);
        }
        request.items().forEach(this::validateRequestItem);
        long requestId = repository.createRequest(request.driverId(), request.vehicleId());
        request.items().forEach(item -> {
            if (!repository.reserveTire(item.tireId())) {
                throw new BusinessException(ErrorCode.TIRE_001);
            }
            repository.createRequestItem(requestId, item.tireId(), item.confirmedTireNo(), item.ocrRecordId());
        });
        return findRequest(requestId);
    }

    private OcrRecordResponse findOcr(long id) {
        return repository.findOcr(id).orElseThrow(() -> new BusinessException(ErrorCode.TIRE_003));
    }

    private List<OcrRecordResponse.Candidate> toResponseCandidates(List<TireNumberCandidateExtractor.Candidate> candidates) {
        return candidates.stream()
                .map(candidate -> new OcrRecordResponse.Candidate(
                        candidate.candidate(),
                        candidate.confidence(),
                        candidate.sourceText(),
                        candidate.inventoryMatched()))
                .toList();
    }

    private String configuredProviderName() {
        return "TENCENT".equalsIgnoreCase(ocrProperties.getProvider())
                ? "TENCENT_GENERAL_ACCURATE"
                : ocrProperties.getProvider();
    }

    private void validateRequestItem(TireRequestCreateRequest.Item item) {
        TireResponse tire = repository.findTire(item.tireId()).orElseThrow(() -> new BusinessException(ErrorCode.TIRE_002));
        if (repository.tireHasActiveRequest(item.tireId()) || repository.tireClaimed(item.tireId())) {
            throw new BusinessException(ErrorCode.TIRE_001);
        }
        if (!"IN_STOCK".equals(tire.status())) {
            throw new BusinessException(ErrorCode.TIRE_001);
        }
        if (!tire.tireNo().equals(item.confirmedTireNo())) {
            throw new BusinessException(ErrorCode.TIRE_004);
        }
        if (item.ocrRecordId() != null) {
            OcrRecordResponse ocr = findOcr(item.ocrRecordId());
            if (ocr.confirmedText() == null || !tire.tireNo().equals(ocr.confirmedText())) {
                throw new BusinessException(ErrorCode.TIRE_004);
            }
        }
    }
}
