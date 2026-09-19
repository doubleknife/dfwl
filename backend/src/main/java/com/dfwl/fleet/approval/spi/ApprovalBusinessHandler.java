package com.dfwl.fleet.approval.spi;

public interface ApprovalBusinessHandler {

    boolean supports(ApprovalBusinessContext approval);

    default void onCreated(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
    }

    default void onApproved(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
    }

    default void onReturnedToApplicant(ApprovalBusinessContext approval, ApprovalSubmissionContext submission, long operatorId) {
    }
}
