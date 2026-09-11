package com.dfwl.fleet.tire.service;

import com.dfwl.fleet.approval.api.ApprovalResponse;
import com.dfwl.fleet.approval.repository.ApprovalRepository.SubmissionRecord;
import com.dfwl.fleet.approval.service.ApprovalBusinessHandler;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.tire.api.TireRequestResponse;
import com.dfwl.fleet.tire.repository.TireRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
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
    public boolean supports(ApprovalResponse approval) {
        return "TIRE_REQUEST".equals(approval.approvalType()) || "TIRE_REQUEST".equals(approval.businessType());
    }

    @Override
    public void onCreated(ApprovalResponse approval, SubmissionRecord submission, long operatorId) {
        long requestId = requestId(approval, submission);
        repository.attachApproval(requestId, approval.id());
    }

    @Override
    public void onApproved(ApprovalResponse approval, SubmissionRecord submission, long operatorId) {
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
    public void onReturnedToApplicant(ApprovalResponse approval, SubmissionRecord submission, long operatorId) {
        long requestId = requestId(approval, submission);
        repository.releaseRequestTires(requestId);
        repository.updateRequestStatus(requestId, "RETURNED_TO_APPLICANT");
    }

    private long requestId(ApprovalResponse approval, SubmissionRecord submission) {
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

    private LocalDateTime installTime(SubmissionRecord submission) {
        JsonNode snapshot = readSnapshot(submission);
        JsonNode value = snapshot.get("installTime");
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(value.asText());
    }

    private JsonNode readSnapshot(SubmissionRecord submission) {
        try {
            return objectMapper.readTree(submission.businessSnapshotJson());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYS_003);
        }
    }
}
