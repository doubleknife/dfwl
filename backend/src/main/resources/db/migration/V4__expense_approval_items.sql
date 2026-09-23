-- Existing approved expenses normally have one row per approval. Preserve that row as item 1.
-- Historical anomalies with multiple rows retain distinct, traceable legacy item numbers.
ALTER TABLE expense_entry ADD COLUMN approval_item_no VARCHAR(64) NULL;

UPDATE expense_entry e
JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY approval_instance_id ORDER BY id) AS item_order
    FROM expense_entry
    WHERE source_type = 'APPROVAL' AND approval_instance_id IS NOT NULL
) existing ON existing.id = e.id
SET e.approval_item_no = CASE WHEN existing.item_order = 1 THEN '1'
                              ELSE CONCAT('LEGACY_', e.id) END;

ALTER TABLE expense_entry
    ADD UNIQUE KEY uk_expense_approval_item (approval_instance_id, approval_item_no);
