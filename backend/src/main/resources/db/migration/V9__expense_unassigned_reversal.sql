-- An unassigned route expense remains a company expense when it is reversed.
-- Keep the original route attribution empty on both sides of the reversal pair.
ALTER TABLE expense_entry DROP CHECK chk_expense_attr;
ALTER TABLE expense_entry ADD CONSTRAINT chk_expense_attr CHECK (
  (attribution_type = 'ROUTE' AND (route_id IS NOT NULL OR status IN ('PENDING_ATTRIBUTION', 'REVERSED', 'REVERSAL')))
  OR (attribution_type = 'DAILY' AND route_id IS NULL)
);

-- Old settlement rows are immutable. The new column is only populated for V2 generations.
ALTER TABLE settlement_version
  ADD COLUMN unassigned_expense DECIMAL(18,2) NOT NULL DEFAULT 0.00;
