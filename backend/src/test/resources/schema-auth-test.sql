-- Generated from database/database.sql for H2 MySQL mode.
CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(100) NOT NULL,
  is_system_fixed TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);

CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone VARCHAR(32) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role_id BIGINT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  last_login_at TIMESTAMP NULL,
  version INT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_sys_user_phone UNIQUE (phone)
);

CREATE TABLE sys_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  permission_code VARCHAR(128) NOT NULL,
  permission_name VARCHAR(128) NOT NULL,
  permission_type VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  CONSTRAINT uk_sys_permission_code UNIQUE (permission_code)
);

CREATE TABLE sys_role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE driver (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,
  driver_type VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  phone VARCHAR(32) NOT NULL,
  vehicle_license_no VARCHAR(64) NULL,
  driving_license_no VARCHAR(64) NULL,
  id_card_no VARCHAR(64) NULL,
  birth_date DATE NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  CONSTRAINT uk_driver_user UNIQUE (user_id)
);

CREATE TABLE vehicle (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plate_no VARCHAR(32) NOT NULL,
  insurance_complete TINYINT NOT NULL DEFAULT 0,
  energy_type VARCHAR(32) NOT NULL,
  max_load DECIMAL(18,3) NOT NULL,
  load_standard_type VARCHAR(32) NOT NULL,
  load_standard_percent DECIMAL(8,4) NULL,
  load_standard_min_load DECIMAL(18,3) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  CONSTRAINT uk_vehicle_plate UNIQUE (plate_no),
  CONSTRAINT chk_vehicle_load_standard CHECK (
    (load_standard_type = 'PERCENT' AND load_standard_percent IS NOT NULL AND load_standard_min_load IS NULL)
    OR (load_standard_type = 'FIXED' AND load_standard_percent IS NULL AND load_standard_min_load IS NOT NULL)
  )
);

CREATE TABLE trailer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trailer_no VARCHAR(64) NOT NULL,
  plate_no VARCHAR(32) NULL,
  insurance_complete TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  CONSTRAINT uk_trailer_no UNIQUE (trailer_no)
);

CREATE TABLE driver_vehicle_current (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  driver_id BIGINT NOT NULL,
  vehicle_id BIGINT NOT NULL,
  bound_at TIMESTAMP NOT NULL,
  bound_by BIGINT NOT NULL,
  CONSTRAINT uk_dvc_driver UNIQUE (driver_id),
  CONSTRAINT uk_dvc_vehicle UNIQUE (vehicle_id)
);

CREATE TABLE driver_vehicle_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  driver_id BIGINT NOT NULL,
  vehicle_id BIGINT NOT NULL,
  bind_time TIMESTAMP NOT NULL,
  unbind_time TIMESTAMP NULL,
  bind_by BIGINT NOT NULL,
  unbind_by BIGINT NULL,
  unbind_reason VARCHAR(500) NULL
);

CREATE TABLE vehicle_trailer_current (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  vehicle_id BIGINT NOT NULL,
  trailer_id BIGINT NOT NULL,
  bound_at TIMESTAMP NOT NULL,
  bound_by BIGINT NOT NULL,
  CONSTRAINT uk_vtc_vehicle UNIQUE (vehicle_id),
  CONSTRAINT uk_vtc_trailer UNIQUE (trailer_id)
);

CREATE TABLE vehicle_trailer_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  vehicle_id BIGINT NOT NULL,
  trailer_id BIGINT NOT NULL,
  bind_time TIMESTAMP NOT NULL,
  unbind_time TIMESTAMP NULL,
  bind_by BIGINT NOT NULL,
  unbind_by BIGINT NULL,
  unbind_reason VARCHAR(500) NULL
);

CREATE TABLE product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_product_name UNIQUE (name)
);

CREATE TABLE route_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stable_code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  customer_name VARCHAR(128) NOT NULL,
  product_id BIGINT NOT NULL,
  loading_place VARCHAR(255) NOT NULL,
  unloading_place VARCHAR(255) NOT NULL,
  default_unit_price DECIMAL(18,2) NOT NULL,
  default_information_fee DECIMAL(18,2) NOT NULL,
  default_driver_salary DECIMAL(18,2) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  version INT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_route_template_stable_code UNIQUE (stable_code),
  CONSTRAINT fk_route_template_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT chk_route_template_defaults CHECK (default_unit_price >= 0 AND default_information_fee >= 0 AND default_driver_salary >= 0)
);

CREATE TABLE dispatch_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_date DATE NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  request_digest CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_by BIGINT NOT NULL,
  confirmed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_dispatch_request_key UNIQUE (request_key)
);

CREATE TABLE dispatch_batch_row (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  row_no INT NOT NULL,
  template_id BIGINT NOT NULL,
  driver_id BIGINT NOT NULL,
  outsourced_plate_no VARCHAR(32) NULL,
  actual_unit_price DECIMAL(18,2) NOT NULL,
  actual_information_fee DECIMAL(18,2) NOT NULL,
  initial_salary DECIMAL(18,2) NOT NULL,
  remark VARCHAR(500) NULL,
  result_code VARCHAR(64) NULL,
  task_id BIGINT NULL,
  CONSTRAINT uk_dispatch_batch_row UNIQUE (batch_id, row_no),
  CONSTRAINT fk_dispatch_row_batch FOREIGN KEY (batch_id) REFERENCES dispatch_batch(id),
  CONSTRAINT fk_dispatch_row_template FOREIGN KEY (template_id) REFERENCES route_template(id),
  CONSTRAINT chk_dispatch_amounts CHECK (actual_unit_price >= 0 AND actual_information_fee >= 0 AND initial_salary >= 0)
);

CREATE TABLE transport_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  import_business_key CHAR(64) NULL,
  task_no VARCHAR(64) NOT NULL,
  template_id BIGINT NOT NULL,
  template_code_snapshot VARCHAR(64) NOT NULL,
  template_name_snapshot VARCHAR(128) NOT NULL,
  customer_name_snapshot VARCHAR(128) NOT NULL,
  product_name_snapshot VARCHAR(128) NOT NULL,
  loading_place_snapshot VARCHAR(255) NOT NULL,
  unloading_place_snapshot VARCHAR(255) NOT NULL,
  actual_unit_price DECIMAL(18,2) NOT NULL,
  actual_information_fee DECIMAL(18,2) NOT NULL,
  business_date DATE NOT NULL,
  current_participant_id BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
  effective_weight_version_id BIGINT NULL,
  cancel_reason VARCHAR(500) NULL,
  cancelled_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  version INT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  CONSTRAINT uk_task_no UNIQUE (task_no),
  CONSTRAINT uk_task_import_business_key UNIQUE (import_business_key),
  CONSTRAINT fk_task_template FOREIGN KEY (template_id) REFERENCES route_template(id),
  CONSTRAINT chk_task_amounts CHECK (actual_unit_price >= 0 AND actual_information_fee >= 0),
  CONSTRAINT chk_task_status CHECK (status IN ('UNPUBLISHED', 'PUBLISHED', 'IN_TRANSIT', 'TRANSFER_PENDING', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE task_amount_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  amount_kind VARCHAR(32) NOT NULL,
  before_amount DECIMAL(18,2) NOT NULL,
  after_amount DECIMAL(18,2) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT NOT NULL,
  operated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_task_amount_history_task FOREIGN KEY (task_id) REFERENCES transport_task(id),
  CONSTRAINT chk_task_amount_kind CHECK (amount_kind IN ('UNIT_PRICE', 'INFORMATION_FEE')),
  CONSTRAINT chk_task_amount_nonnegative CHECK (before_amount >= 0 AND after_amount >= 0)
);

CREATE TABLE task_command_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  command_type VARCHAR(32) NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  request_digest CHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_task_command_request UNIQUE (task_id, command_type, request_key),
  CONSTRAINT fk_task_command_task FOREIGN KEY (task_id) REFERENCES transport_task(id)
);

CREATE TABLE task_status_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  operator_id BIGINT NOT NULL,
  operated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reason VARCHAR(500) NULL,
  CONSTRAINT fk_task_status_history_task FOREIGN KEY (task_id) REFERENCES transport_task(id)
);

CREATE TABLE transport_participant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  sequence_no INT NOT NULL,
  driver_id BIGINT NOT NULL,
  driver_type_snapshot VARCHAR(32) NOT NULL,
  company_vehicle_id BIGINT NULL,
  vehicle_plate_snapshot VARCHAR(32) NULL,
  trailer_id BIGINT NULL,
  trailer_plate_snapshot VARCHAR(32) NULL,
  outsourced_plate_no VARCHAR(32) NULL,
  source_type VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  assigned_at TIMESTAMP NULL,
  departed_at TIMESTAMP NULL,
  ended_at TIMESTAMP NULL,
  end_reason VARCHAR(500) NULL,
  transfer_reason VARCHAR(500) NULL,
  salary_amount DECIMAL(18,2) NOT NULL,
  is_current TINYINT NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  active_task_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN is_current = 1 THEN task_id ELSE NULL END),
  CONSTRAINT uk_participant_task_sequence UNIQUE (task_id, sequence_no),
  CONSTRAINT uk_participant_one_active UNIQUE (active_task_id),
  CONSTRAINT fk_participant_task FOREIGN KEY (task_id) REFERENCES transport_task(id),
  CONSTRAINT chk_participant_salary_nonnegative CHECK (salary_amount >= 0),
  CONSTRAINT chk_participant_source CHECK (source_type IN ('INITIAL', 'TRANSFER')),
  CONSTRAINT chk_participant_status CHECK (status IN ('ASSIGNED', 'IN_TRANSIT', 'TRANSFERRED', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT chk_participant_driver_type CHECK (driver_type_snapshot IN ('INTERNAL', 'OUTSOURCED')),
  CONSTRAINT chk_participant_vehicle_type CHECK (
    (driver_type_snapshot = 'INTERNAL' AND company_vehicle_id IS NOT NULL AND outsourced_plate_no IS NULL)
    OR (driver_type_snapshot = 'OUTSOURCED' AND company_vehicle_id IS NULL AND outsourced_plate_no IS NOT NULL))
);

CREATE TABLE participant_salary_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  participant_id BIGINT NOT NULL,
  before_amount DECIMAL(18,2) NULL,
  after_amount DECIMAL(18,2) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT NOT NULL,
  operated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_salary_history_participant FOREIGN KEY (participant_id) REFERENCES transport_participant(id),
  CONSTRAINT chk_participant_salary_before CHECK (before_amount IS NULL OR before_amount >= 0),
  CONSTRAINT chk_participant_salary_after CHECK (after_amount >= 0)
);

CREATE TABLE departure_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  participant_id BIGINT NOT NULL,
  departed_at TIMESTAMP NOT NULL,
  driver_id_snapshot BIGINT NOT NULL,
  driver_type_snapshot VARCHAR(32) NOT NULL,
  company_vehicle_id BIGINT NULL,
  vehicle_plate_snapshot VARCHAR(32) NULL,
  trailer_plate_snapshot VARCHAR(32) NULL,
  outsourced_plate_no VARCHAR(32) NULL,
  CONSTRAINT uk_departure_participant UNIQUE (participant_id),
  CONSTRAINT fk_departure_task FOREIGN KEY (task_id) REFERENCES transport_task(id),
  CONSTRAINT fk_departure_participant FOREIGN KEY (participant_id) REFERENCES transport_participant(id)
);

CREATE TABLE task_weight_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  gross_weight DECIMAL(18,3) NOT NULL,
  tare_weight DECIMAL(18,3) NOT NULL,
  net_weight DECIMAL(18,3) NOT NULL,
  load_standard_threshold DECIMAL(18,3) NULL,
  load_standard_met TINYINT NULL,
  source_type VARCHAR(32) NOT NULL,
  operator_id BIGINT NOT NULL,
  operation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reason VARCHAR(500) NULL,
  attachment_ids VARCHAR(500) NULL,
  CONSTRAINT uk_route_weight_ver UNIQUE (task_id, version_no),
  CONSTRAINT fk_weight_version_task FOREIGN KEY (task_id) REFERENCES transport_task(id),
  CONSTRAINT chk_route_weight_positive CHECK (gross_weight > 0 AND tare_weight > 0),
  CONSTRAINT chk_route_weight_gt CHECK (gross_weight > tare_weight)
);

CREATE TABLE expense_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  expense_no VARCHAR(64) NOT NULL,
  approval_instance_id BIGINT NULL,
  approval_item_no INT NULL,
  application_scope VARCHAR(16) NOT NULL,
  current_task_id BIGINT NULL,
  vehicle_id BIGINT NOT NULL,
  driver_id BIGINT NULL,
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
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  CONSTRAINT uk_expense_no UNIQUE (expense_no),
  CONSTRAINT uk_expense_approval_item UNIQUE (approval_instance_id, approval_item_no),
  CONSTRAINT uk_expense_source_key UNIQUE (source_type, source_record_key),
  CONSTRAINT chk_expense_scope CHECK (application_scope IN ('DAILY', 'TASK')),
  CONSTRAINT chk_expense_task_scope CHECK (application_scope = 'DAILY' OR current_task_id IS NOT NULL)
);




CREATE TABLE expense_attribution_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  expense_id BIGINT NOT NULL,
  previous_task_id BIGINT NULL,
  new_task_id BIGINT NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT NOT NULL,
  operated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_exp_attr_expense FOREIGN KEY (expense_id) REFERENCES expense_entry(id)
);


CREATE TABLE expense_reversal_link (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  original_expense_id BIGINT NOT NULL,
  reversal_expense_id BIGINT NOT NULL,
  replacement_expense_id BIGINT NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_reversal_original UNIQUE (original_expense_id),
  CONSTRAINT uk_reversal_expense UNIQUE (reversal_expense_id)
);


CREATE TABLE approval_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stable_code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  expense_type VARCHAR(32) NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_approval_template_code UNIQUE (stable_code),
  CONSTRAINT chk_approval_template_type CHECK (business_type IN ('EXPENSE', 'GENERAL', 'TIRE_REPLACEMENT'))
);

CREATE TABLE approval_flow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP NULL,
  active_template_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN template_id ELSE NULL END),
  CONSTRAINT uk_flow_template_version UNIQUE (template_id, version_no),
  CONSTRAINT uk_flow_one_active_template UNIQUE (active_template_id),
  CONSTRAINT fk_flow_template FOREIGN KEY (template_id) REFERENCES approval_template (id)
);

CREATE TABLE approval_flow_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  flow_id BIGINT NOT NULL,
  node_order INT NOT NULL,
  node_name VARCHAR(128) NOT NULL,
  approver_user_id BIGINT NOT NULL,
  allow_return TINYINT NOT NULL DEFAULT 1,
  CONSTRAINT uk_flow_node_order UNIQUE (flow_id, node_order)
);



CREATE TABLE approval_flow_initiator_role (
  flow_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (flow_id, role_id),
  CONSTRAINT fk_approval_initiator_flow FOREIGN KEY (flow_id) REFERENCES approval_flow(id),
  CONSTRAINT fk_approval_initiator_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
);

CREATE TABLE approval_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  approval_no VARCHAR(64) NOT NULL,
  template_id BIGINT NOT NULL,
  flow_id BIGINT NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  application_scope VARCHAR(16) NULL,
  applicant_user_id BIGINT NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  request_digest CHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  current_node_id BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_approval_no UNIQUE (approval_no),
  CONSTRAINT uk_approval_applicant_request UNIQUE (applicant_user_id, request_key),
  CONSTRAINT chk_approval_application_scope CHECK (application_scope IS NULL OR application_scope IN ('DAILY', 'TASK')),
  CONSTRAINT fk_approval_instance_template FOREIGN KEY (template_id) REFERENCES approval_template(id),
  CONSTRAINT fk_approval_instance_flow FOREIGN KEY (flow_id) REFERENCES approval_flow(id)
);

CREATE TABLE approval_submission_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  instance_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  business_snapshot_json VARCHAR(4000) NOT NULL,
  submitted_by BIGINT NOT NULL,
  submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_approval_submit_ver UNIQUE (instance_id, version_no),
  CONSTRAINT fk_submission_instance FOREIGN KEY (instance_id) REFERENCES approval_instance(id)
);

CREATE TABLE approval_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  instance_id BIGINT NOT NULL,
  submission_version_id BIGINT NOT NULL,
  node_id BIGINT NOT NULL,
  approver_user_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  invalidated TINYINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  CONSTRAINT uk_approval_task_version_node UNIQUE (instance_id, submission_version_id, node_id, approver_user_id)
);

CREATE TABLE approval_action (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  operator_id BIGINT NOT NULL,
  comment VARCHAR(2000) NULL,
  target_flow_node_id BIGINT NULL,
  target_step_name_snapshot VARCHAR(128) NULL,
  operated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE approval_expense_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  instance_id BIGINT NOT NULL,
  submission_version_id BIGINT NOT NULL,
  item_no INT NOT NULL,
  expense_type VARCHAR(32) NOT NULL,
  business_date DATE NOT NULL,
  vehicle_id BIGINT NOT NULL,
  driver_id BIGINT NULL,
  description VARCHAR(1000) NULL,
  amount DECIMAL(18,2) NOT NULL,
  receipt_no VARCHAR(128) NULL,
  invoice_no VARCHAR(128) NULL,
  remark VARCHAR(1000) NULL,
  station_name VARCHAR(128) NULL,
  target_task_id BIGINT NULL,
  CONSTRAINT uk_approval_expense_item UNIQUE (submission_version_id, item_no),
  CONSTRAINT chk_approval_expense_positive CHECK (amount > 0),
  CONSTRAINT fk_approval_expense_instance FOREIGN KEY (instance_id) REFERENCES approval_instance(id),
  CONSTRAINT fk_approval_expense_submission FOREIGN KEY (submission_version_id) REFERENCES approval_submission_version(id)
);

CREATE TABLE file_attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_type VARCHAR(64) NOT NULL,
  owner_id BIGINT NOT NULL,
  purpose VARCHAR(64) NOT NULL,
  storage_key VARCHAR(512) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NULL,
  file_size BIGINT NOT NULL,
  file_hash VARCHAR(128) NULL,
  uploaded_by BIGINT NOT NULL,
  uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE approval_submission_attachment (
  submission_version_id BIGINT NOT NULL,
  attachment_id BIGINT NOT NULL,
  PRIMARY KEY (submission_version_id, attachment_id),
  CONSTRAINT uk_approval_submission_attachment UNIQUE (attachment_id),
  CONSTRAINT fk_approval_attachment_submission FOREIGN KEY (submission_version_id) REFERENCES approval_submission_version(id),
  CONSTRAINT fk_approval_attachment_file FOREIGN KEY (attachment_id) REFERENCES file_attachment(id)
);

CREATE TABLE task_unload_evidence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  participant_id BIGINT NOT NULL,
  weight_version_id BIGINT NOT NULL,
  attachment_id BIGINT NOT NULL,
  uploaded_by BIGINT NOT NULL,
  bound_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_route_unload_attachment UNIQUE (attachment_id)
);


CREATE TABLE ocr_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  attachment_id BIGINT NOT NULL,
  ocr_provider VARCHAR(64) NULL,
  provider_request_id VARCHAR(128) NULL,
  raw_result_json VARCHAR(4000) NULL,
  recognized_text VARCHAR(512) NULL,
  candidate_text VARCHAR(512) NULL,
  candidates_json VARCHAR(4000) NULL,
  ocr_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
  error_code VARCHAR(128) NULL,
  error_message VARCHAR(1000) NULL,
  confirmed_text VARCHAR(512) NULL,
  confirmed_by BIGINT NULL,
  confirmed_at TIMESTAMP NULL
);

CREATE TABLE tire (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tire_no VARCHAR(128) NOT NULL,
  barcode VARCHAR(128) NULL,
  material_description VARCHAR(1000) NOT NULL,
  arrived_at TIMESTAMP NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
  version INT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  CONSTRAINT uk_tire_no UNIQUE (tire_no),
  CONSTRAINT uk_tire_barcode UNIQUE (barcode)
);

CREATE TABLE tire_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tire_id BIGINT NOT NULL,
  field_name VARCHAR(64) NOT NULL,
  before_value VARCHAR(1000) NULL,
  after_value VARCHAR(1000) NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT NOT NULL,
  operated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_tire_history_tire FOREIGN KEY (tire_id) REFERENCES tire(id)
);

CREATE TABLE tire_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  applicant_driver_id BIGINT NOT NULL,
  approval_instance_id BIGINT NULL,
  vehicle_id BIGINT NOT NULL,
  confirmed_tire_no VARCHAR(128) NOT NULL,
  ocr_record_id BIGINT NOT NULL,
  photo_attachment_id BIGINT NOT NULL,
  tire_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_tire_request_approval UNIQUE (approval_instance_id)
);

CREATE INDEX idx_tire_request_tire_status ON tire_request(tire_id, status);



CREATE TABLE import_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  business_type VARCHAR(32) NOT NULL,
  template_version VARCHAR(32) NOT NULL,
  source_attachment_id BIGINT NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirmed_at TIMESTAMP NULL,
  CONSTRAINT uk_import_request UNIQUE (business_type, request_key)
);

CREATE TABLE import_row (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  import_task_id BIGINT NOT NULL,
  row_no INT NOT NULL,
  raw_json VARCHAR(4000) NOT NULL,
  validation_status VARCHAR(32) NOT NULL,
  result_code VARCHAR(64) NULL,
  result_message VARCHAR(1000) NULL,
  target_id BIGINT NULL,
  CONSTRAINT uk_import_row UNIQUE (import_task_id, row_no)
);

CREATE TABLE settlement_month (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  month_key CHAR(7) NOT NULL,
  latest_version_no INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_settlement_month UNIQUE (month_key)
);

CREATE TABLE settlement_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  month_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  generated_by BIGINT NOT NULL,
  income_total DECIMAL(18,2) NOT NULL DEFAULT 0,
  expense_total DECIMAL(18,2) NOT NULL DEFAULT 0,
  salary_total DECIMAL(18,2) NOT NULL DEFAULT 0,
  information_fee_total DECIMAL(18,2) NOT NULL DEFAULT 0,
  profit_total DECIMAL(18,2) NOT NULL DEFAULT 0,
  CONSTRAINT uk_settlement_version UNIQUE (month_id, version_no),
  CONSTRAINT fk_settlement_month FOREIGN KEY (month_id) REFERENCES settlement_month(id)
);

CREATE TABLE settlement_detail (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  version_id BIGINT NOT NULL,
  fact_type VARCHAR(32) NOT NULL,
  fact_id BIGINT NOT NULL,
  fact_version INT NOT NULL,
  fact_snapshot_json VARCHAR(4000) NOT NULL,
  business_date DATE NOT NULL,
  income_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  expense_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  salary_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  information_fee_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  profit_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  CONSTRAINT fk_settlement_detail_version FOREIGN KEY (version_id) REFERENCES settlement_version(id)
);

CREATE TABLE settlement_diff (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  version_id BIGINT NOT NULL,
  prior_version_id BIGINT NULL,
  fact_type VARCHAR(32) NOT NULL,
  fact_key VARCHAR(128) NOT NULL,
  change_type VARCHAR(32) NOT NULL,
  before_snapshot_json VARCHAR(4000) NULL,
  after_snapshot_json VARCHAR(4000) NULL,
  income_delta DECIMAL(18,2) NOT NULL DEFAULT 0,
  expense_delta DECIMAL(18,2) NOT NULL DEFAULT 0,
  profit_delta DECIMAL(18,2) NOT NULL DEFAULT 0,
  reason VARCHAR(500) NULL,
  CONSTRAINT fk_settlement_diff_version FOREIGN KEY (version_id) REFERENCES settlement_version(id)
);

CREATE TABLE audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module VARCHAR(64) NOT NULL,
  business_type VARCHAR(64) NOT NULL,
  business_id BIGINT NULL,
  operation_type VARCHAR(64) NOT NULL,
  before_json VARCHAR(4000) NULL,
  after_json VARCHAR(4000) NULL,
  operator_id BIGINT NOT NULL,
  operation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reason VARCHAR(500) NULL,
  ip VARCHAR(64) NULL,
  terminal VARCHAR(128) NULL,
  request_id VARCHAR(64) NULL
);

INSERT INTO sys_role (role_code, role_name, is_system_fixed)
VALUES
  ('DRIVER', '司机', 1),
  ('ADMIN', '管理员', 1);

INSERT INTO sys_permission (permission_code, permission_name, permission_type)
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
  ('tire:view', '查看轮胎库存', 'API'),
  ('tire:import', '轮胎入库与导入历史', 'API'),
  ('tire:manage', '维护轮胎资料', 'API'),
  ('tire:request:view', '查看换胎申请', 'API'),
  ('tire:request:create', '发起换胎申请', 'API'),
  ('tire:request:process', '处理换胎申请', 'API'),
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

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'DRIVER'
  AND p.permission_code IN ('task:view', 'task:depart', 'task:unload', 'salary:mine', 'approval:create');
CREATE TABLE in_app_notification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  event_key VARCHAR(160) NOT NULL,
  type VARCHAR(40) NOT NULL,
  title VARCHAR(100) NOT NULL,
  summary VARCHAR(300) NOT NULL,
  business_type VARCHAR(40) NOT NULL,
  business_id BIGINT NOT NULL,
  read_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_notification_event_user UNIQUE (event_key, user_id),
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
