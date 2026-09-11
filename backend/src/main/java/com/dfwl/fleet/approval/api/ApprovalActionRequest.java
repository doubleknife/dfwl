package com.dfwl.fleet.approval.api;

public record ApprovalActionRequest(
        String comment,
        Integer targetNodeOrder
) {
}
