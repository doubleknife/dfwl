package com.dfwl.fleet.tire.service;

import com.dfwl.fleet.approval.spi.ApprovalBusinessContext;
import com.dfwl.fleet.approval.spi.ApprovalSubmissionContext;
import com.dfwl.fleet.approval.spi.ApprovalBusinessHandler;
import com.dfwl.fleet.approval.spi.ApprovalResubmissionContext;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.tire.dto.response.OcrRecordResponse;
import com.dfwl.fleet.tire.dto.response.TireRequestResponse;
import com.dfwl.fleet.tire.repository.TireRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TireApprovalBusinessHandler implements ApprovalBusinessHandler {

    private final TireRepository repository;
    private final ObjectMapper objectMapper;

    public TireApprovalBusinessHandler(TireRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ApprovalBusinessContext approval) {
        return "TIRE_REQUEST".equals(approval.approvalType()) || "TIRE_REQUEST".equals(approval.businessType());
    }

    @Override
    public void onCreated(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
        long requestId = requestId(approval, submission);
        if (!repository.attachApproval(requestId, approval.approvalInstanceId())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
    }

    @Override
    public void onApproved(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
        long requestId = requestId(approval, submission);
        TireRequestResponse request = repository.findRequest(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
        LocalDateTime installTime = installTime(submission);
        for (TireRequestResponse.Item item : request.items()) {
            if (repository.tireClaimed(item.tireId())) {
                continue;
            }
            if (!repository.claimTire(item.tireId(), request.driverId(), request.vehicleId(), installTime)) {
                throw new BusinessException(ErrorCode.TIRE_001);
            }
            repository.insertClaim(item.tireId(), request.driverId(), request.vehicleId(), requestId, installTime, "APPROVAL");
        }
        repository.updateRequestStatus(requestId, "APPROVED");
    }

    @Override
    public void onReturnedToApplicant(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
        long requestId = requestId(approval, submission);
        repository.releaseRequestTires(requestId);
        repository.updateRequestStatus(requestId, "RETURNED_TO_APPLICANT");
    }

    @Override
    public void onResubmitted(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
        long requestId = requestId(approval, submission);
        TireRequestResponse request = repository.findRequest(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
        if (!"RETURNED_TO_APPLICANT".equals(request.status())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        for (TireRequestResponse.Item item : request.items()) {
            if (!repository.reserveTire(item.tireId())) {
                throw new BusinessException(ErrorCode.TIRE_001);
            }
        }
        repository.updateRequestStatus(requestId, "PENDING");
    }

    @Override
    public ApprovalResubmissionContext prepareResubmission(ApprovalBusinessContext approval,
                                                            Map<String, Object> requestedSnapshot, long operatorId) {
        if (approval.businessId() == null) throw new BusinessException(ErrorCode.APPROVAL_002);
        TireRequestResponse request = repository.findRequest(approval.businessId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_001));
        if (!"RETURNED_TO_APPLICANT".equals(request.status())
                || !approval.approvalInstanceId().equals(request.approvalInstanceId())) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("requestId", request.id());
        canonical.put("driverId", request.driverId());
        canonical.put("vehicleId", request.vehicleId());
        List<Map<String, Object>> items = new ArrayList<>();
        List<Long> evidence = new ArrayList<>();
        for (TireRequestResponse.Item item : request.items()) {
            Map<String, Object> canonicalItem = new LinkedHashMap<>();
            canonicalItem.put("tireId", item.tireId());
            canonicalItem.put("confirmedTireNo", item.confirmedTireNo());
            if (item.ocrRecordId() != null) {
                OcrRecordResponse ocr = repository.findOcr(item.ocrRecordId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.TIRE_003));
                if (!item.confirmedTireNo().equals(ocr.confirmedText())) {
                    throw new BusinessException(ErrorCode.TIRE_004);
                }
                canonicalItem.put("ocrRecordId", item.ocrRecordId());
                canonicalItem.put("ocrAttachmentId", ocr.attachmentId());
                evidence.add(ocr.attachmentId());
            }
            items.add(canonicalItem);
        }
        canonical.put("items", items);
        requireMatchingRequest(requestedSnapshot, canonical);
        return new ApprovalResubmissionContext(canonical, evidence.stream().distinct().toList());
    }

    private void requireMatchingRequest(Map<String, Object> supplied, Map<String, Object> canonical) {
        if (supplied == null || supplied.keySet().stream()
                .anyMatch(key -> !Set.of("requestId", "driverId", "vehicleId", "items").contains(key))) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        for (String key : List.of("requestId", "driverId", "vehicleId")) {
            if (supplied.containsKey(key) && !String.valueOf(canonical.get(key)).equals(String.valueOf(supplied.get(key)))) {
                throw new BusinessException(ErrorCode.APPROVAL_002);
            }
        }
        if (!supplied.containsKey("items")) return;
        if (!(supplied.get("items") instanceof List<?> requestedItems)) {
            throw new BusinessException(ErrorCode.APPROVAL_002);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actualItems = (List<Map<String, Object>>) canonical.get("items");
        if (requestedItems.size() != actualItems.size()) throw new BusinessException(ErrorCode.APPROVAL_002);
        for (int index = 0; index < requestedItems.size(); index++) {
            if (!(requestedItems.get(index) instanceof Map<?, ?> requestedItem)) {
                throw new BusinessException(ErrorCode.APPROVAL_002);
            }
            Map<String, Object> actual = actualItems.get(index);
            for (var entry : requestedItem.entrySet()) {
                if (!actual.containsKey(entry.getKey())
                        || !String.valueOf(actual.get(entry.getKey())).equals(String.valueOf(entry.getValue()))) {
                    throw new BusinessException(ErrorCode.APPROVAL_002);
                }
            }
        }
    }

    private long requestId(ApprovalBusinessContext approval, ApprovalSubmissionContext submission) {
        if (approval.businessId() != null) {
            return approval.businessId();
        }
        JsonNode snapshot = readSnapshot(submission);
        JsonNode value = snapshot.get("requestId");
        if (value == null || value.isNull()) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        return value.asLong();
    }

    private LocalDateTime installTime(ApprovalSubmissionContext submission) {
        JsonNode snapshot = readSnapshot(submission);
        JsonNode value = snapshot.get("installTime");
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(value.asText());
    }

    private JsonNode readSnapshot(ApprovalSubmissionContext submission) {
        try {
            return objectMapper.readTree(submission.businessSnapshotJson());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYS_003);
        }
    }
}
