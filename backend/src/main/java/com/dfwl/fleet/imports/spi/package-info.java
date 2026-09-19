/**
 * Stable, JDK-only contracts between imports coordination and domain-owned handlers.
 *
 * <p>Imports owns task/row lifecycle, parsing and JSON persistence, handler discovery,
 * batch/import-history duplicate coordination, result aggregation, idempotent task commit,
 * failure export and REQUIRES_NEW row transactions. Handlers receive no API DTO,
 * repository, mutable batch set or JDBC state through these contracts.</p>
 *
 * <p>The four domain handlers own their keys, reference checks, business duplicate rules
 * and synchronous business writes. Parsing helpers remain implementation details.
 * Preview validates through the handler; commit rechecks current facts within its handler
 * before writing. Existing domain-specific validation ordering and error keys are retained.
 * Expense keeps its existing database-constraint duplicate behavior; no new business
 * precheck is implied by the validateDuplicate hook.</p>
 *
 * <p>Business writes and import_row final results share the dispatcher's row transaction.
 * Database exceptions escape the row transaction; the outer ImportService records
 * IMPORT_DB_ERROR through markFailure and continues other rows. Existing runtime/JSON
 * format-error mapping is unchanged. Handlers neither own import history queries nor
 * start independent transactions or asynchronous business effects.</p>
 *
 * <p>Spring discovers implementations through the interface list. Registration rejects
 * missing or duplicate types and does not depend on bean order. The standalone enum and
 * contexts do not depend on application services or on any business-domain types.</p>
 */
package com.dfwl.fleet.imports.spi;
