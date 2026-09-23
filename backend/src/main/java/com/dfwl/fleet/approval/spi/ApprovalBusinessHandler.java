package com.dfwl.fleet.approval.spi;

import java.util.List;
import java.util.Map;

public interface ApprovalBusinessHandler {

    boolean supports(ApprovalBusinessContext approval);

    default void onCreated(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
    }

    default void onApproved(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
    }

    default void onReturnedToApplicant(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
    }

    default void onResubmitted(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
    }

    default ApprovalResubmissionContext prepareResubmission(ApprovalBusinessContext approval,
                                                              Map<String, Object> requestedSnapshot, long operatorId) {
        return new ApprovalResubmissionContext(requestedSnapshot, List.of());
    }
}
