-- Stable template identity; existing approval_flow rows remain immutable flow versions.
CREATE TABLE approval_template (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  template_code VARCHAR(64) NOT NULL,
  category VARCHAR(16) NOT NULL,
  approval_type VARCHAR(32) NOT NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_approval_template_code (template_code),
  CONSTRAINT chk_approval_template_category CHECK (category IN ('TIRE', 'EXPENSE', 'OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE approval_flow
  ADD COLUMN template_id BIGINT UNSIGNED NULL,
  ADD COLUMN evidence_requirement VARCHAR(16) NOT NULL DEFAULT 'OPTIONAL',
  ADD COLUMN configuration_status VARCHAR(16) NOT NULL DEFAULT 'NEEDS_REVIEW';

-- The pre-template approval type is a deterministic legacy template code.
-- Historical roles and expense options cannot be inferred and remain unconfigured.
INSERT INTO approval_template (template_code, category, approval_type, created_by, created_at)
SELECT approval_type,
       CASE WHEN approval_type = 'TIRE_REQUEST' THEN 'TIRE'
            WHEN approval_type = 'EXPENSE' THEN 'EXPENSE'
            ELSE 'OTHER' END,
       approval_type, MIN(created_by), MIN(created_at)
FROM approval_flow
GROUP BY approval_type;

UPDATE approval_flow f
JOIN approval_template t ON t.approval_type = f.approval_type
SET f.template_id = t.id;

ALTER TABLE approval_flow
  MODIFY COLUMN template_id BIGINT UNSIGNED NOT NULL,
  DROP INDEX uk_flow_type_version,
  ADD UNIQUE KEY uk_flow_template_version (template_id, version_no),
  ADD KEY idx_flow_template_status (template_id, status),
  ADD CONSTRAINT fk_flow_template FOREIGN KEY (template_id) REFERENCES approval_template (id);

-- NULL values permit any number of inactive versions. The unique non-NULL value
-- makes two ACTIVE versions for one template impossible even under concurrent writes.
ALTER TABLE approval_flow
  ADD COLUMN active_template_id BIGINT UNSIGNED
    GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN template_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_flow_one_active_template (active_template_id);

CREATE TABLE approval_flow_initiator_role (
  flow_id BIGINT UNSIGNED NOT NULL,
  role_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (flow_id, role_id),
  CONSTRAINT fk_flow_initiator_flow FOREIGN KEY (flow_id) REFERENCES approval_flow (id),
  CONSTRAINT fk_flow_initiator_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_flow_expense_config (
  flow_id BIGINT UNSIGNED PRIMARY KEY,
  expense_type VARCHAR(32) NOT NULL,
  allow_single TINYINT UNSIGNED NOT NULL,
  allow_excel TINYINT UNSIGNED NOT NULL,
  allow_route TINYINT UNSIGNED NOT NULL,
  allow_daily TINYINT UNSIGNED NOT NULL,
  CONSTRAINT fk_flow_expense_config FOREIGN KEY (flow_id) REFERENCES approval_flow (id),
  CONSTRAINT chk_expense_input_mode CHECK (allow_single = 1 OR allow_excel = 1),
  CONSTRAINT chk_expense_attribution CHECK (allow_route = 1 OR allow_daily = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
