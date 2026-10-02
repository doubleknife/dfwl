-- V3 first-release single baseline. No development user or password is seeded.

CREATE TABLE sys_role (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(100) NOT NULL,
  is_system_fixed TINYINT UNSIGNED NOT NULL DEFAULT 0,
  status TINYINT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  phone VARCHAR(32) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role_id BIGINT UNSIGNED NOT NULL,
  status TINYINT UNSIGNED NOT NULL DEFAULT 1,
  last_login_at DATETIME(3) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT UNSIGNED NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_sys_user_phone (phone),
  KEY idx_sys_user_role_status (role_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_permission (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  permission_code VARCHAR(128) NOT NULL,
  permission_name VARCHAR(128) NOT NULL,
  permission_type VARCHAR(32) NOT NULL,
  status TINYINT UNSIGNED NOT NULL DEFAULT 1,
  UNIQUE KEY uk_sys_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role_permission (
  role_id BIGINT UNSIGNED NOT NULL,
  permission_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE driver (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NULL,
  driver_type VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  phone VARCHAR(32) NOT NULL,
  vehicle_license_no VARCHAR(64) NULL,
  driving_license_no VARCHAR(64) NULL,
  id_card_no VARCHAR(64) NULL,
  birth_date DATE NULL,
  status TINYINT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_driver_user (user_id),
  KEY idx_driver_phone (phone),
  KEY idx_driver_type_status (driver_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE vehicle (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  plate_no VARCHAR(32) NOT NULL,
  insurance_complete TINYINT UNSIGNED NOT NULL DEFAULT 0,
  energy_type VARCHAR(32) NOT NULL,
  max_load DECIMAL(18,3) NOT NULL,
  load_standard_type VARCHAR(32) NOT NULL,
  load_standard_percent DECIMAL(8,4) NULL,
  load_standard_min_load DECIMAL(18,3) NULL,
  status TINYINT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_vehicle_plate (plate_no),
  CONSTRAINT chk_vehicle_load_standard CHECK (
    (load_standard_type = 'PERCENT' AND load_standard_percent IS NOT NULL AND load_standard_min_load IS NULL)
    OR (load_standard_type = 'FIXED' AND load_standard_percent IS NULL AND load_standard_min_load IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trailer (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  trailer_no VARCHAR(64) NOT NULL,
  plate_no VARCHAR(32) NULL,
  insurance_complete TINYINT UNSIGNED NOT NULL DEFAULT 0,
  status TINYINT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_trailer_no (trailer_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE driver_vehicle_current (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  driver_id BIGINT UNSIGNED NOT NULL,
  vehicle_id BIGINT UNSIGNED NOT NULL,
  bound_at DATETIME(3) NOT NULL,
  bound_by BIGINT UNSIGNED NOT NULL,
  UNIQUE KEY uk_dvc_driver (driver_id),
  UNIQUE KEY uk_dvc_vehicle (vehicle_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE driver_vehicle_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  driver_id BIGINT UNSIGNED NOT NULL,
  vehicle_id BIGINT UNSIGNED NOT NULL,
  bind_time DATETIME(3) NOT NULL,
  unbind_time DATETIME(3) NULL,
  bind_by BIGINT UNSIGNED NOT NULL,
  unbind_by BIGINT UNSIGNED NULL,
  unbind_reason VARCHAR(500) NULL,
  KEY idx_dvh_driver_time (driver_id, bind_time),
  KEY idx_dvh_vehicle_time (vehicle_id, bind_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE vehicle_trailer_current (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  vehicle_id BIGINT UNSIGNED NOT NULL,
  trailer_id BIGINT UNSIGNED NOT NULL,
  bound_at DATETIME(3) NOT NULL,
  bound_by BIGINT UNSIGNED NOT NULL,
  UNIQUE KEY uk_vtc_vehicle (vehicle_id),
  UNIQUE KEY uk_vtc_trailer (trailer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE vehicle_trailer_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  vehicle_id BIGINT UNSIGNED NOT NULL,
  trailer_id BIGINT UNSIGNED NOT NULL,
  bind_time DATETIME(3) NOT NULL,
  unbind_time DATETIME(3) NULL,
  bind_by BIGINT UNSIGNED NOT NULL,
  unbind_by BIGINT UNSIGNED NULL,
  unbind_reason VARCHAR(500) NULL,
  KEY idx_vth_vehicle_time (vehicle_id, bind_time),
  KEY idx_vth_trailer_time (trailer_id, bind_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  enabled TINYINT UNSIGNED NOT NULL DEFAULT 1,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_product_name (name),
  KEY idx_product_enabled (enabled, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE route_template (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  stable_code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  customer_name VARCHAR(128) NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  loading_place VARCHAR(255) NOT NULL,
  unloading_place VARCHAR(255) NOT NULL,
  default_unit_price DECIMAL(18,2) NOT NULL,
  default_information_fee DECIMAL(18,2) NOT NULL,
  default_driver_salary DECIMAL(18,2) NOT NULL,
  enabled TINYINT UNSIGNED NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_route_template_stable_code (stable_code),
  KEY idx_route_template_enabled (enabled, id),
  CONSTRAINT fk_route_template_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT chk_route_template_defaults CHECK (default_unit_price >= 0 AND default_information_fee >= 0 AND default_driver_salary >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dispatch_batch (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  business_date DATE NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  request_digest CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  confirmed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_dispatch_request_key (request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dispatch_batch_row (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT UNSIGNED NOT NULL,
  row_no INT UNSIGNED NOT NULL,
  template_id BIGINT UNSIGNED NOT NULL,
  driver_id BIGINT UNSIGNED NOT NULL,
  outsourced_plate_no VARCHAR(32) NULL,
  actual_unit_price DECIMAL(18,2) NOT NULL,
  actual_information_fee DECIMAL(18,2) NOT NULL,
  initial_salary DECIMAL(18,2) NOT NULL,
  remark VARCHAR(500) NULL,
  result_code VARCHAR(64) NULL,
  task_id BIGINT UNSIGNED NULL,
  UNIQUE KEY uk_dispatch_batch_row (batch_id, row_no),
  CONSTRAINT fk_dispatch_row_batch FOREIGN KEY (batch_id) REFERENCES dispatch_batch(id),
  CONSTRAINT fk_dispatch_row_template FOREIGN KEY (template_id) REFERENCES route_template(id),
  CONSTRAINT chk_dispatch_amounts CHECK (actual_unit_price >= 0 AND actual_information_fee >= 0 AND initial_salary >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transport_task (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  import_business_key CHAR(64) NULL,
  task_no VARCHAR(64) NOT NULL,
  template_id BIGINT UNSIGNED NOT NULL,
  template_code_snapshot VARCHAR(64) NOT NULL,
  template_name_snapshot VARCHAR(128) NOT NULL,
  customer_name_snapshot VARCHAR(128) NOT NULL,
  product_name_snapshot VARCHAR(128) NOT NULL,
  loading_place_snapshot VARCHAR(255) NOT NULL,
  unloading_place_snapshot VARCHAR(255) NOT NULL,
  actual_unit_price DECIMAL(18,2) NOT NULL,
  actual_information_fee DECIMAL(18,2) NOT NULL,
  business_date DATE NOT NULL,
  current_participant_id BIGINT UNSIGNED NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
  effective_weight_version_id BIGINT UNSIGNED NULL,
  cancel_reason VARCHAR(500) NULL,
  cancelled_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT UNSIGNED NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_task_no (task_no),
  UNIQUE KEY uk_task_import_business_key (import_business_key),
  KEY idx_task_status_date (status, business_date),
  KEY idx_task_template_date (template_id, business_date),
  KEY idx_task_current_participant (current_participant_id),
  CONSTRAINT fk_task_template FOREIGN KEY (template_id) REFERENCES route_template(id),
  CONSTRAINT chk_task_amounts CHECK (actual_unit_price >= 0 AND actual_information_fee >= 0),
  CONSTRAINT chk_task_status CHECK (status IN ('UNPUBLISHED', 'PUBLISHED', 'IN_TRANSIT', 'TRANSFER_PENDING', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_amount_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  amount_kind VARCHAR(32) NOT NULL,
  before_amount DECIMAL(18,2) NOT NULL,
  after_amount DECIMAL(18,2) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_task_amount_history (task_id, operated_at),
  CONSTRAINT fk_task_amount_history_task FOREIGN KEY (task_id) REFERENCES transport_task(id),
  CONSTRAINT chk_task_amount_kind CHECK (amount_kind IN ('UNIT_PRICE', 'INFORMATION_FEE')),
  CONSTRAINT chk_task_amount_nonnegative CHECK (before_amount >= 0 AND after_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_command_request (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  command_type VARCHAR(32) NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  request_digest CHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_task_command_request (task_id, command_type, request_key),
  CONSTRAINT fk_task_command_task FOREIGN KEY (task_id) REFERENCES transport_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_status_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reason VARCHAR(500) NULL,
  KEY idx_task_status_hist (task_id, operated_at),
  CONSTRAINT fk_task_status_history_task FOREIGN KEY (task_id) REFERENCES transport_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transport_participant (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  sequence_no INT UNSIGNED NOT NULL,
  driver_id BIGINT UNSIGNED NOT NULL,
  driver_type_snapshot VARCHAR(32) NOT NULL,
  company_vehicle_id BIGINT UNSIGNED NULL,
  vehicle_plate_snapshot VARCHAR(32) NULL,
  trailer_id BIGINT UNSIGNED NULL,
  trailer_plate_snapshot VARCHAR(32) NULL,
  outsourced_plate_no VARCHAR(32) NULL,
  source_type VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  assigned_at DATETIME(3) NULL,
  departed_at DATETIME(3) NULL,
  ended_at DATETIME(3) NULL,
  end_reason VARCHAR(500) NULL,
  transfer_reason VARCHAR(500) NULL,
  salary_amount DECIMAL(18,2) NOT NULL,
  is_current TINYINT UNSIGNED NOT NULL DEFAULT 1,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT UNSIGNED NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  active_task_id BIGINT UNSIGNED GENERATED ALWAYS AS
    (CASE WHEN is_current = 1 THEN task_id ELSE NULL END) STORED,
  UNIQUE KEY uk_participant_task_sequence (task_id, sequence_no),
  UNIQUE KEY uk_participant_one_active (active_task_id),
  KEY idx_participant_route_current (task_id, status, sequence_no),
  KEY idx_participant_driver_status (driver_id, status),
  KEY idx_participant_company_vehicle_route (company_vehicle_id, task_id),
  CONSTRAINT fk_participant_task FOREIGN KEY (task_id) REFERENCES transport_task(id),
  CONSTRAINT chk_participant_salary_nonnegative CHECK (salary_amount >= 0),
  CONSTRAINT chk_participant_source CHECK (source_type IN ('INITIAL', 'TRANSFER')),
  CONSTRAINT chk_participant_status CHECK (status IN ('ASSIGNED', 'IN_TRANSIT', 'TRANSFERRED', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT chk_participant_driver_type CHECK (driver_type_snapshot IN ('INTERNAL', 'OUTSOURCED')),
  CONSTRAINT chk_participant_vehicle_type CHECK (
    (driver_type_snapshot = 'INTERNAL' AND company_vehicle_id IS NOT NULL AND outsourced_plate_no IS NULL)
    OR (driver_type_snapshot = 'OUTSOURCED' AND company_vehicle_id IS NULL AND outsourced_plate_no IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE participant_salary_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  participant_id BIGINT UNSIGNED NOT NULL,
  before_amount DECIMAL(18,2) NULL,
  after_amount DECIMAL(18,2) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_participant_salary_history (participant_id, operated_at),
  CONSTRAINT fk_salary_history_participant FOREIGN KEY (participant_id) REFERENCES transport_participant(id),
  CONSTRAINT chk_participant_salary_before CHECK (before_amount IS NULL OR before_amount >= 0),
  CONSTRAINT chk_participant_salary_after CHECK (after_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE departure_event (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  participant_id BIGINT UNSIGNED NOT NULL,
  departed_at DATETIME(3) NOT NULL,
  driver_id_snapshot BIGINT UNSIGNED NOT NULL,
  driver_type_snapshot VARCHAR(32) NOT NULL,
  company_vehicle_id BIGINT UNSIGNED NULL,
  vehicle_plate_snapshot VARCHAR(32) NULL,
  trailer_plate_snapshot VARCHAR(32) NULL,
  outsourced_plate_no VARCHAR(32) NULL,
  UNIQUE KEY uk_departure_participant (participant_id),
  KEY idx_departure_driver_time (driver_id_snapshot, departed_at),
  KEY idx_departure_vehicle_time (company_vehicle_id, departed_at),
  CONSTRAINT fk_departure_task FOREIGN KEY (task_id) REFERENCES transport_task(id),
  CONSTRAINT fk_departure_participant FOREIGN KEY (participant_id) REFERENCES transport_participant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_weight_version (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  version_no INT UNSIGNED NOT NULL,
  gross_weight DECIMAL(18,3) NOT NULL,
  tare_weight DECIMAL(18,3) NOT NULL,
  net_weight DECIMAL(18,3) NOT NULL,
  load_standard_threshold DECIMAL(18,3) NULL,
  load_standard_met TINYINT NULL,
  source_type VARCHAR(32) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  operation_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reason VARCHAR(500) NULL,
  attachment_ids VARCHAR(500) NULL,
  UNIQUE KEY uk_route_weight_ver (task_id, version_no),
  KEY idx_route_weight_route (task_id, operation_time),
  CONSTRAINT fk_weight_version_task FOREIGN KEY (task_id) REFERENCES transport_task(id),
  CONSTRAINT chk_route_weight_positive CHECK (gross_weight > 0 AND tare_weight > 0),
  CONSTRAINT chk_route_weight_gt CHECK (gross_weight > tare_weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expense_entry (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  expense_no VARCHAR(64) NOT NULL,
  approval_instance_id BIGINT UNSIGNED NULL,
  approval_item_no INT UNSIGNED NULL,
  application_scope VARCHAR(16) NOT NULL,
  current_task_id BIGINT UNSIGNED NULL,
  vehicle_id BIGINT UNSIGNED NOT NULL,
  driver_id BIGINT UNSIGNED NULL,
  expense_type VARCHAR(32) NOT NULL,
  business_date DATE NOT NULL,
  description VARCHAR(1000) NULL,
  amount DECIMAL(18,2) NOT NULL,
  receipt_no VARCHAR(128) NULL,
  invoice_no VARCHAR(128) NULL,
  remark VARCHAR(1000) NULL,
  station_name VARCHAR(255) NULL,
  source_type VARCHAR(16) NOT NULL,
  source_record_key VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT UNSIGNED NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_expense_no (expense_no),
  KEY idx_expense_vehicle_date (vehicle_id, business_date),
  KEY idx_expense_task_type (current_task_id, expense_type, status),
  KEY idx_expense_date_scope (business_date, application_scope, status),
  KEY idx_expense_approval (approval_instance_id),
  UNIQUE KEY uk_expense_approval_item (approval_instance_id, approval_item_no),
  UNIQUE KEY uk_expense_source_key (source_type, source_record_key),
  CONSTRAINT chk_expense_scope CHECK (application_scope IN ('DAILY', 'TASK')),
  CONSTRAINT chk_expense_task_scope CHECK (application_scope = 'DAILY' OR current_task_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;




CREATE TABLE expense_attribution_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  expense_id BIGINT UNSIGNED NOT NULL,
  previous_task_id BIGINT UNSIGNED NULL,
  new_task_id BIGINT UNSIGNED NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_exp_attr_hist_expense (expense_id, operated_at),
  CONSTRAINT fk_exp_attr_expense FOREIGN KEY (expense_id) REFERENCES expense_entry(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE expense_reversal_link (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  original_expense_id BIGINT UNSIGNED NOT NULL,
  reversal_expense_id BIGINT UNSIGNED NOT NULL,
  replacement_expense_id BIGINT UNSIGNED NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_reversal_original (original_expense_id),
  UNIQUE KEY uk_reversal_expense (reversal_expense_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE approval_template (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  stable_code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  expense_type VARCHAR(32) NULL,
  enabled TINYINT UNSIGNED NOT NULL DEFAULT 1,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  UNIQUE KEY uk_approval_template_code (stable_code),
  CONSTRAINT chk_approval_template_type CHECK (business_type IN ('EXPENSE', 'GENERAL', 'TIRE_REPLACEMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_flow (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  template_id BIGINT UNSIGNED NOT NULL,
  version_no INT UNSIGNED NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  published_at DATETIME(3) NULL,
  active_template_id BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN template_id ELSE NULL END) STORED,
  UNIQUE KEY uk_flow_template_version (template_id, version_no),
  UNIQUE KEY uk_flow_one_active_template (active_template_id),
  KEY idx_flow_template_status (template_id, status),
  CONSTRAINT fk_flow_template FOREIGN KEY (template_id) REFERENCES approval_template (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_flow_node (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  flow_id BIGINT UNSIGNED NOT NULL,
  node_order INT UNSIGNED NOT NULL,
  node_name VARCHAR(128) NOT NULL,
  approver_user_id BIGINT UNSIGNED NOT NULL,
  allow_return TINYINT UNSIGNED NOT NULL DEFAULT 1,
  UNIQUE KEY uk_flow_node_order (flow_id, node_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_flow_initiator_role (
  flow_id BIGINT UNSIGNED NOT NULL,
  role_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (flow_id, role_id),
  CONSTRAINT fk_approval_initiator_flow FOREIGN KEY (flow_id) REFERENCES approval_flow(id),
  CONSTRAINT fk_approval_initiator_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;



CREATE TABLE approval_instance (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  approval_no VARCHAR(64) NOT NULL,
  template_id BIGINT UNSIGNED NOT NULL,
  flow_id BIGINT UNSIGNED NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  application_scope VARCHAR(16) NULL,
  applicant_user_id BIGINT UNSIGNED NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  request_digest CHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  current_node_id BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  UNIQUE KEY uk_approval_no (approval_no),
  UNIQUE KEY uk_approval_applicant_request (applicant_user_id, request_key),
  KEY idx_approval_type_status (business_type, status),
  CONSTRAINT chk_approval_application_scope CHECK (application_scope IS NULL OR application_scope IN ('DAILY', 'TASK')),
  CONSTRAINT fk_approval_instance_template FOREIGN KEY (template_id) REFERENCES approval_template(id),
  CONSTRAINT fk_approval_instance_flow FOREIGN KEY (flow_id) REFERENCES approval_flow(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_submission_version (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL,
  version_no INT UNSIGNED NOT NULL,
  business_snapshot_json JSON NOT NULL,
  submitted_by BIGINT UNSIGNED NOT NULL,
  submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_approval_submit_ver (instance_id, version_no),
  CONSTRAINT fk_submission_instance FOREIGN KEY (instance_id) REFERENCES approval_instance(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_task (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL,
  submission_version_id BIGINT UNSIGNED NOT NULL,
  node_id BIGINT UNSIGNED NOT NULL,
  approver_user_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  invalidated TINYINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  KEY idx_task_approver_status (approver_user_id, status, created_at),
  KEY idx_task_instance (instance_id, node_id),
  UNIQUE KEY uk_approval_task_version_node (instance_id, submission_version_id, node_id, approver_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_action (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  comment VARCHAR(2000) NULL,
  target_flow_node_id BIGINT UNSIGNED NULL,
  target_step_name_snapshot VARCHAR(128) NULL,
  operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_action_task_time (task_id, operated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_expense_item (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  instance_id BIGINT UNSIGNED NOT NULL,
  submission_version_id BIGINT UNSIGNED NOT NULL,
  item_no INT UNSIGNED NOT NULL,
  expense_type VARCHAR(32) NOT NULL,
  business_date DATE NOT NULL,
  vehicle_id BIGINT UNSIGNED NOT NULL,
  driver_id BIGINT UNSIGNED NULL,
  description VARCHAR(1000) NULL,
  amount DECIMAL(18,2) NOT NULL,
  receipt_no VARCHAR(128) NULL,
  invoice_no VARCHAR(128) NULL,
  remark VARCHAR(1000) NULL,
  station_name VARCHAR(128) NULL,
  target_task_id BIGINT UNSIGNED NULL,
  UNIQUE KEY uk_approval_expense_item (submission_version_id, item_no),
  CONSTRAINT chk_approval_expense_positive CHECK (amount > 0),
  CONSTRAINT fk_approval_expense_instance FOREIGN KEY (instance_id) REFERENCES approval_instance(id),
  CONSTRAINT fk_approval_expense_submission FOREIGN KEY (submission_version_id) REFERENCES approval_submission_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE file_attachment (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  owner_type VARCHAR(64) NOT NULL,
  owner_id BIGINT UNSIGNED NOT NULL,
  purpose VARCHAR(64) NOT NULL,
  storage_key VARCHAR(512) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NULL,
  file_size BIGINT UNSIGNED NOT NULL,
  file_hash VARCHAR(128) NULL,
  uploaded_by BIGINT UNSIGNED NOT NULL,
  uploaded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_file_owner (owner_type, owner_id, purpose),
  KEY idx_file_hash (file_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_submission_attachment (
  submission_version_id BIGINT UNSIGNED NOT NULL,
  attachment_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (submission_version_id, attachment_id),
  UNIQUE KEY uk_approval_submission_attachment (attachment_id),
  CONSTRAINT fk_approval_attachment_submission FOREIGN KEY (submission_version_id)
    REFERENCES approval_submission_version(id),
  CONSTRAINT fk_approval_attachment_file FOREIGN KEY (attachment_id) REFERENCES file_attachment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- BIZ-1D: immutable association from an original unload weight version to each ticket.
-- A temporary ROUTE attachment has no row here until the unload transaction succeeds.
CREATE TABLE task_unload_evidence (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  participant_id BIGINT UNSIGNED NOT NULL,
  weight_version_id BIGINT UNSIGNED NOT NULL,
  attachment_id BIGINT UNSIGNED NOT NULL,
  uploaded_by BIGINT UNSIGNED NOT NULL,
  bound_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_route_unload_attachment (attachment_id),
  KEY idx_route_unload_route (task_id, weight_version_id),
  KEY idx_route_unload_participant (participant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE ocr_record (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  attachment_id BIGINT UNSIGNED NOT NULL,
  ocr_provider VARCHAR(64) NULL,
  provider_request_id VARCHAR(128) NULL,
  raw_result_json JSON NULL,
  recognized_text VARCHAR(512) NULL,
  candidate_text VARCHAR(512) NULL,
  candidates_json JSON NULL,
  ocr_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
  error_code VARCHAR(128) NULL,
  error_message VARCHAR(1000) NULL,
  confirmed_text VARCHAR(512) NULL,
  confirmed_by BIGINT UNSIGNED NULL,
  confirmed_at DATETIME(3) NULL,
  KEY idx_ocr_attachment (attachment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tire (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  tire_no VARCHAR(128) NOT NULL,
  barcode VARCHAR(128) NULL,
  material_description VARCHAR(1000) NOT NULL,
  arrived_at DATETIME(3) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT UNSIGNED NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_tire_no (tire_no),
  KEY idx_tire_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tire_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  tire_id BIGINT UNSIGNED NOT NULL,
  field_name VARCHAR(64) NOT NULL,
  before_value VARCHAR(1000) NULL,
  after_value VARCHAR(1000) NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_tire_history (tire_id, operated_at),
  CONSTRAINT fk_tire_history_tire FOREIGN KEY (tire_id) REFERENCES tire(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tire_request (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  applicant_driver_id BIGINT UNSIGNED NOT NULL,
  approval_instance_id BIGINT UNSIGNED NULL,
  vehicle_id BIGINT UNSIGNED NOT NULL,
  confirmed_tire_no VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_tire_request_approval (approval_instance_id),
  KEY idx_tire_request_driver_status (applicant_driver_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;



CREATE TABLE import_task (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  business_type VARCHAR(32) NOT NULL,
  template_version VARCHAR(32) NOT NULL,
  source_attachment_id BIGINT UNSIGNED NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  confirmed_at DATETIME(3) NULL,
  UNIQUE KEY uk_import_request (business_type, request_key),
  KEY idx_import_type_time (business_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE import_row (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  import_task_id BIGINT UNSIGNED NOT NULL,
  row_no INT UNSIGNED NOT NULL,
  raw_json JSON NOT NULL,
  validation_status VARCHAR(32) NOT NULL,
  result_code VARCHAR(64) NULL,
  result_message VARCHAR(1000) NULL,
  target_id BIGINT UNSIGNED NULL,
  UNIQUE KEY uk_import_row (import_task_id, row_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE settlement_month (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  month_key CHAR(7) NOT NULL,
  latest_version_no INT UNSIGNED NOT NULL DEFAULT 0,
  UNIQUE KEY uk_settlement_month (month_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE settlement_version (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  month_id BIGINT UNSIGNED NOT NULL,
  version_no INT UNSIGNED NOT NULL,
  status VARCHAR(32) NOT NULL,
  generated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  generated_by BIGINT UNSIGNED NOT NULL,
  income_total DECIMAL(18,2) NOT NULL DEFAULT 0,
  expense_total DECIMAL(18,2) NOT NULL DEFAULT 0,
  salary_total DECIMAL(18,2) NOT NULL DEFAULT 0,
  information_fee_total DECIMAL(18,2) NOT NULL DEFAULT 0,
  profit_total DECIMAL(18,2) NOT NULL DEFAULT 0,
  UNIQUE KEY uk_settlement_version (month_id, version_no),
  CONSTRAINT fk_settlement_month FOREIGN KEY (month_id) REFERENCES settlement_month(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE settlement_detail (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  version_id BIGINT UNSIGNED NOT NULL,
  fact_type VARCHAR(32) NOT NULL,
  fact_id BIGINT UNSIGNED NOT NULL,
  fact_version INT UNSIGNED NOT NULL,
  fact_snapshot_json JSON NOT NULL,
  business_date DATE NOT NULL,
  income_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  expense_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  salary_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  information_fee_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  profit_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  KEY idx_settle_detail_ver_type (version_id, fact_type),
  KEY idx_settle_detail_fact (fact_type, fact_id),
  CONSTRAINT fk_settlement_detail_version FOREIGN KEY (version_id) REFERENCES settlement_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE settlement_diff (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  version_id BIGINT UNSIGNED NOT NULL,
  prior_version_id BIGINT UNSIGNED NULL,
  fact_type VARCHAR(32) NOT NULL,
  fact_key VARCHAR(128) NOT NULL,
  change_type VARCHAR(32) NOT NULL,
  before_snapshot_json JSON NULL,
  after_snapshot_json JSON NULL,
  income_delta DECIMAL(18,2) NOT NULL DEFAULT 0,
  expense_delta DECIMAL(18,2) NOT NULL DEFAULT 0,
  profit_delta DECIMAL(18,2) NOT NULL DEFAULT 0,
  reason VARCHAR(500) NULL,
  KEY idx_settle_diff_version (version_id, change_type),
  CONSTRAINT fk_settlement_diff_version FOREIGN KEY (version_id) REFERENCES settlement_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_log (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  module VARCHAR(64) NOT NULL,
  business_type VARCHAR(64) NOT NULL,
  business_id BIGINT UNSIGNED NULL,
  operation_type VARCHAR(64) NOT NULL,
  before_json JSON NULL,
  after_json JSON NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  operation_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reason VARCHAR(500) NULL,
  ip VARCHAR(64) NULL,
  terminal VARCHAR(128) NULL,
  request_id VARCHAR(64) NULL,
  KEY idx_audit_business (business_type, business_id, operation_time),
  KEY idx_audit_operator_time (operator_id, operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO sys_role (role_code, role_name, is_system_fixed)
VALUES
  ('DRIVER', '司机', 1),
  ('ADMIN', '管理员', 1);

INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type)
VALUES
  ('vehicle:list', '车辆列表', 'API'),
  ('vehicle:add', '车辆新增', 'API'),
  ('vehicle:view', '车辆查看', 'API'),
  ('vehicle:edit', '车辆编辑', 'API'),
  ('vehicle:delete', '车辆删除', 'API'),
  ('vehicle:bindDriver', '车辆绑定司机', 'API'),
  ('vehicle:unbindDriver', '车辆解绑司机', 'API'),
  ('vehicle:bindingHistory', '车辆绑定历史', 'API'),
  ('trailer:list', '车挂列表', 'API'),
  ('trailer:add', '车挂新增', 'API'),
  ('trailer:edit', '车挂编辑', 'API'),
  ('trailer:bindVehicle', '车挂绑定车辆', 'API'),
  ('trailer:unbindVehicle', '车挂解绑车辆', 'API'),
  ('trailer:bindingHistory', '车挂绑定历史', 'API'),
  ('driver:list', '司机列表', 'API'),
  ('driver:add', '司机新增', 'API'),
  ('driver:edit', '司机编辑', 'API'),
  ('driver:bindVehicle', '司机绑定车辆', 'API'),
  ('driver:unbindVehicle', '司机解绑车辆', 'API'),
  ('driver:resetPassword', '司机重置密码', 'API'),
  ('template:view', '查看线路模板', 'API'),
  ('template:manage', '维护线路模板', 'API'),
  ('task:view', '查看运输任务', 'API'),
  ('task:participants:view', '查看任务参与人', 'API'),
  ('task:weight:view', '查看任务重量证据', 'API'),
  ('dispatch:view', '查看调度工作台', 'API'),
  ('dispatch:batch', '批量确认发布任务', 'API'),
  ('task:import:view', '查看任务导入', 'API'),
  ('task:import:submit', '提交任务导入', 'API'),
  ('task:cancel', '取消未发车任务', 'API'),
  ('task:transfer', '转派运输任务', 'API'),
  ('task:depart', '确认发车', 'API'),
  ('task:unload', '确认卸货', 'API'),
  ('task:weight:adjust', '调整任务重量', 'API'),
  ('task:amount:adjust', '调整任务金额', 'API'),
  ('salary:view', '工资查看', 'API'),
  ('salary:adjust', '调整参与工资', 'API'),
  ('salary:export', '导出工资台账', 'API'),
  ('salary:mine', '本人薪资查看', 'API'),
  ('attendance:view', '查看司机出勤', 'API'),
  ('attendance:export', '导出司机出勤', 'API'),
  ('dispatch:ledger:view', '查看车辆调度月账', 'API'),
  ('dispatch:ledger:export', '导出车辆调度月账', 'API'),
  ('approval:create', '发起审批与费用申请', 'API'),
  ('approval:process', '处理审批', 'API'),
  ('approval:return', '打回审批', 'API'),
  ('approval:history:view', '查看审批记录', 'API'),
  ('approval:flow:manage', '维护审批流程', 'API'),
  ('expense:ledger:view', '查看费用台账', 'API'),
  ('expense:attribution:edit', '调整费用任务归属', 'API'),
  ('expense:reversal', '冲销正式费用', 'API'),
  ('expense:export', '导出费用台账', 'API'),
  ('report:task:view', '查看任务经营', 'API'),
  ('report:template:view', '查看线路模板经营', 'API'),
  ('report:company:view', '查看公司经营', 'API'),
  ('settlement:view', '查看月结版本', 'API'),
  ('settlement:generate', '生成月结版本', 'API'),
  ('user:manage', '用户管理', 'API'),
  ('role:manage', '角色管理', 'API'),
  ('permission:manage', '权限管理', 'API'),
  ('audit:view', '审计查看', 'API');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN';

-- 正式初始化库不植入管理员账号或密码；管理员由部署初始化流程创建。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'DRIVER'
  AND p.permission_code IN ('task:view', 'task:depart', 'task:unload', 'salary:mine', 'approval:create');

-- V7：只供当前用户查看的站内通知；事件键保证同一用户不会收到重复业务通知。
CREATE TABLE in_app_notification (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  event_key VARCHAR(160) NOT NULL,
  type VARCHAR(40) NOT NULL,
  title VARCHAR(100) NOT NULL,
  summary VARCHAR(300) NOT NULL,
  business_type VARCHAR(40) NOT NULL,
  business_id BIGINT UNSIGNED NOT NULL,
  read_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_notification_event_user (event_key, user_id),
  KEY idx_notification_user_time (user_id, created_at, id),
  KEY idx_notification_user_unread (user_id, read_at),
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
