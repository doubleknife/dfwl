package com.dfwl.fleet.imports.api;

public record ImportRowResponse(
        Long id,
        Integer rowNo,
        String rawDataJson,
        String normalizedDataJson,
        String previewStatus,
        String previewErrorCode,
        String previewErrorMessage,
        String finalStatus,
        String finalErrorCode,
        String finalErrorMessage,
        String businessType,
        Long businessId,
        String businessUniqueKey
) {
}
