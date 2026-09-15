package com.dfwl.fleet.imports.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record ImportPreviewRequest(
        @NotBlank String businessType,
        Long templateId,
        @NotNull Long originalFileId,
        List<@Valid ImportRowRequest> rows
) {
    public record ImportRowRequest(
            @NotNull Integer rowNo,
            @NotNull Map<String, Object> rawData,
            String businessUniqueKey
    ) {
    }
}
