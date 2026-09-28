-- BIZ-1A: participant history is authoritative; route_task columns remain compatibility projections.
ALTER TABLE route_task
  ADD COLUMN current_participant_id BIGINT UNSIGNED NULL,
  ADD KEY idx_route_current_participant (current_participant_id);

ALTER TABLE audit_log ADD COLUMN request_id VARCHAR(64) NULL;

CREATE TABLE route_participant (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  route_id BIGINT UNSIGNED NOT NULL,
  sequence_no INT UNSIGNED NOT NULL,
  driver_id BIGINT UNSIGNED NOT NULL,
  driver_type_snapshot VARCHAR(32) NULL,
  company_vehicle_id BIGINT UNSIGNED NULL,
  outsourced_plate_no VARCHAR(32) NULL,
  source_type VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  assigned_at DATETIME(3) NULL,
  departed_at DATETIME(3) NULL,
  ended_at DATETIME(3) NULL,
  end_reason VARCHAR(500) NULL,
  transfer_reason VARCHAR(500) NULL,
  salary_amount DECIMAL(18,2) NULL,
  historical_data_complete TINYINT UNSIGNED NOT NULL DEFAULT 1,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT UNSIGNED NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  active_route_id BIGINT UNSIGNED GENERATED ALWAYS AS
    (CASE WHEN status IN ('ASSIGNED', 'IN_TRANSIT') THEN route_id ELSE NULL END) STORED,
  UNIQUE KEY uk_participant_route_sequence (route_id, sequence_no),
  UNIQUE KEY uk_participant_one_active (active_route_id),
  KEY idx_participant_route_current (route_id, status, sequence_no),
  KEY idx_participant_driver_status (driver_id, status),
  KEY idx_participant_company_vehicle_route (company_vehicle_id, route_id),
  CONSTRAINT chk_participant_salary_nonnegative CHECK (salary_amount IS NULL OR salary_amount >= 0),
  CONSTRAINT chk_participant_source CHECK (source_type IN ('INITIAL', 'TRANSFER')),
  CONSTRAINT chk_participant_status CHECK (status IN ('ASSIGNED', 'IN_TRANSIT', 'TRANSFERRED', 'COMPLETED', 'CANCELLED', 'LEGACY_ENDED')),
  CONSTRAINT chk_participant_history_flag CHECK (historical_data_complete IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE route_participant_salary_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  participant_id BIGINT UNSIGNED NOT NULL,
  before_amount DECIMAL(18,2) NULL,
  after_amount DECIMAL(18,2) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  operation_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_participant_salary_history (participant_id, operation_time),
  CONSTRAINT chk_participant_salary_before CHECK (before_amount IS NULL OR before_amount >= 0),
  CONSTRAINT chk_participant_salary_after CHECK (after_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Only the recorded departure driver/vehicle is an execution fact. A current binding is
-- never used to invent an historical vehicle or an outsourced plate. Unknown facts stay NULL.
INSERT INTO route_participant (
  route_id, sequence_no, driver_id, driver_type_snapshot, company_vehicle_id,
  source_type, status, assigned_at, departed_at, ended_at, salary_amount,
  historical_data_complete, created_by, created_at, updated_by
)
SELECT r.id, 1, COALESCE(r.departure_driver_id, r.assigned_driver_id),
       CASE WHEN r.status IN ('PUBLISHED', 'IN_TRANSIT') THEN d.driver_type ELSE NULL END,
       CASE WHEN r.departure_driver_id IS NOT NULL AND d.driver_type = 'INTERNAL'
            THEN r.departure_vehicle_id ELSE NULL END,
       'INITIAL',
       CASE r.status
         WHEN 'PUBLISHED' THEN 'ASSIGNED'
         WHEN 'IN_TRANSIT' THEN 'IN_TRANSIT'
         WHEN 'COMPLETED' THEN 'COMPLETED'
         WHEN 'CANCELLED' THEN 'CANCELLED'
         ELSE 'LEGACY_ENDED'
       END,
       r.created_at, r.departure_time,
       CASE WHEN r.status IN ('PUBLISHED', 'IN_TRANSIT') THEN NULL ELSE COALESCE(r.unload_time, r.updated_at) END,
       COALESCE(r.driver_salary, se.salary_amount),
       0, r.created_by, r.created_at, r.updated_by
FROM route_task r
LEFT JOIN driver d ON d.id = COALESCE(r.departure_driver_id, r.assigned_driver_id)
LEFT JOIN driver_salary_entry se ON se.route_id = r.id
WHERE r.status <> 'UNPUBLISHED'
  AND COALESCE(r.departure_driver_id, r.assigned_driver_id) IS NOT NULL;

UPDATE route_task r
JOIN route_participant p ON p.route_id = r.id AND p.sequence_no = 1
SET r.current_participant_id = p.id;

INSERT INTO sys_permission (permission_code, permission_name, permission_type)
VALUES ('route:transfer', '线路转派', 'API'),
       ('route:salary:adjust', '线路参与工资调整', 'API');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN' AND p.permission_code IN ('route:transfer', 'route:salary:adjust');
