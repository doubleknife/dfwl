package com.dfwl.fleet.imports.spi;

/**
 * Business-domain import extension used by the production imports dispatcher.
 * Discover implementations by an injected list of this interface, never concrete handler
 * imports. One handler owns one type; absent registrations must be rejected and duplicate
 * registrations must fail configuration, rather than picking an arbitrary implementation.
 *
 * Preview and commit both resolve the key, then imports checks key presence and batch
 * duplicates, then validateDuplicate checks domain existence, then imports checks committed
 * import history, then validate checks structure/references. Keeping these steps separate
 * preserves the existing domain-duplicate/history/structure ordering and batch reservation.
 * Import-history queries (including findImportedBusiness) never belong to a handler.
 * commit must recheck live business prerequisites; preview is not authorization to write.
 *
 * Row transactions belong to the imports dispatcher (REQUIRES_NEW). commit joins the
 * current row transaction; handlers must not start another independent transaction,
 * publish async work, or update import_task/import_row. Business writes and the row final
 * result are committed atomically by the dispatcher. Validation failures return results
 * before any business writes; failures after writes must escape and roll back the row.
 * Database/infrastructure exceptions must not be swallowed or converted into a success
 * or business FAILURE. After row rollback the outer imports layer records IMPORT_DB_ERROR
 * in its existing failure transaction and continues other rows. Existing format-error
 * mapping remains the adapter's responsibility; this interface changes no catch policy.
 */
public interface ImportBusinessHandler {
    ImportBusinessType businessType();

    /** Honor an explicit key before deriving the domain key; do not mutate business data. */
    ImportValidationResult resolveBusinessKey(ImportRowContext context);

    /** Read-only domain existence check; import-history duplicate checks stay in imports. */
    ImportValidationResult validateDuplicate(ImportRowContext context);

    /** Read-only field and reference validation, repeated at commit time. */
    ImportValidationResult validate(ImportRowContext context);

    /** Execute synchronous domain writes in the caller's row transaction and return their outcome. */
    ImportCommitResult commit(ImportRowContext context);
}
