package com.dfwl.fleet.approval.spi;

import java.util.List;
import java.util.Map;

/** Business-owned facts and existing files to reference in the next submission version. */
public record ApprovalResubmissionContext(
        Map<String, Object> businessSnapshot,
        List<Long> referencedEvidenceIds) {
}
