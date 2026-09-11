package com.dfwl.fleet.approval.service;

import com.dfwl.fleet.approval.api.ApprovalResponse;
import com.dfwl.fleet.approval.repository.ApprovalRepository.SubmissionRecord;

public interface ApprovalBusinessHandler {

    boolean supports(ApprovalResponse approval);

    default void onCreated(ApprovalResponse approval, SubmissionRecord submission, long operatorId) {
    }

    default void onApproved(ApprovalResponse approval, SubmissionRecord submission, long operatorId) {
    }

    default void onReturnedToApplicant(ApprovalResponse approval, SubmissionRecord submission, long operatorId) {
    }
}
