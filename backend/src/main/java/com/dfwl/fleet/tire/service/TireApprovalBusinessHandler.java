package com.dfwl.fleet.tire.service;

import com.dfwl.fleet.approval.spi.ApprovalBusinessContext;
import com.dfwl.fleet.approval.spi.ApprovalSubmissionContext;
import com.dfwl.fleet.approval.spi.ApprovalBusinessHandler;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.tire.dto.response.TireRequestResponse;
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
    public boolean supports(ApprovalBusinessContext approval) {
        return "TIRE_REQUEST".equals(approval.approvalType()) || "TIRE_REQUEST".equals(approval.businessType());
    }

    @Override
    public void onCreated(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
        long requestId = requestId(approval, submission);
        repository.attachApproval(requestId, approval.approvalInstanceId());
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
