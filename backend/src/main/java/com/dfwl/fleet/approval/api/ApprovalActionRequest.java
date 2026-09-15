package com.dfwl.fleet.approval.api;

import java.util.List;

public record ApprovalActionRequest(
        String comment,
        Integer targetNodeOrder,
        List<Long> attachmentIds
) {
    public ApprovalActionRequest(String comment, Integer targetNodeOrder) {
        this(comment, targetNodeOrder, null);
    }
}
