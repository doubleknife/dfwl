package com.dfwl.fleet.imports.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Business input for one row, not a persistence or HTTP DTO.
 * importRowId is null during preview and identifies the persisted row during commit;
 * expense and salary need it for business provenance. operatorId is the acting user.
 * rowData contains parser-mapped business fields (including nullable/nested values).
 * The existing commit path reads rawDataJson, not normalizedDataJson; future adapters
 * must preserve that source. No cached parsed business object crosses the phases.
 * businessUniqueKey is the optional explicit/persisted key on key resolution, and the
 * resolved key on validation/commit. Row number and task lifecycle remain in imports.
 * The top-level map is snapshotted; handlers must also treat nested values as read-only.
 */
public record ImportRowContext(Long importRowId, Map<String, Object> rowData,
                               String businessUniqueKey, long operatorId) {
    public ImportRowContext {
        rowData = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(rowData, "rowData")));
    }
}
