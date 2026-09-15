CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR "com.dfwl.fleet.testsupport.H2Functions.dateFormat";

CREATE TABLE sys_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(100) NOT NULL,
  is_system_fixed TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (role_code)
);

CREATE TABLE sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
  UNIQUE (phone)
);

CREATE TABLE sys_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  permission_code VARCHAR(128) NOT NULL,
  permission_name VARCHAR(128) NOT NULL,
  permission_type VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  UNIQUE (permission_code)
);

CREATE TABLE sys_role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE driver (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
  deleted_at TIMESTAMP NULL
);

CREATE TABLE vehicle (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
  UNIQUE (plate_no)
);

CREATE TABLE trailer (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  trailer_no VARCHAR(64) NOT NULL,
  plate_no VARCHAR(32) NULL,
  insurance_complete TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  UNIQUE (trailer_no)
);

CREATE TABLE driver_vehicle_current (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  driver_id BIGINT NOT NULL,
  vehicle_id BIGINT NOT NULL,
  bound_at TIMESTAMP NOT NULL,
  bound_by BIGINT NOT NULL,
  UNIQUE (driver_id),
  UNIQUE (vehicle_id)
);

CREATE TABLE driver_vehicle_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  driver_id BIGINT NOT NULL,
  vehicle_id BIGINT NOT NULL,
  bind_time TIMESTAMP NOT NULL,
  unbind_time TIMESTAMP NULL,
  bind_by BIGINT NOT NULL,
  unbind_by BIGINT NULL,
  unbind_reason VARCHAR(500) NULL
);

CREATE TABLE vehicle_trailer_current (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  vehicle_id BIGINT NOT NULL,
  trailer_id BIGINT NOT NULL,
  bound_at TIMESTAMP NOT NULL,
  bound_by BIGINT NOT NULL,
  UNIQUE (vehicle_id),
  UNIQUE (trailer_id)
);

CREATE TABLE vehicle_trailer_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  vehicle_id BIGINT NOT NULL,
  trailer_id BIGINT NOT NULL,
  bind_time TIMESTAMP NOT NULL,
  unbind_time TIMESTAMP NULL,
  bind_by BIGINT NOT NULL,
  unbind_by BIGINT NULL,
  unbind_reason VARCHAR(500) NULL
);

CREATE TABLE customer (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_code VARCHAR(64) NULL,
  customer_name VARCHAR(128) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_code VARCHAR(64) NULL,
  product_name VARCHAR(128) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE route_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  route_no VARCHAR(64) NOT NULL,
  external_route_no VARCHAR(128) NULL,
  trip_sequence VARCHAR(64) NULL,
  business_unique_key VARCHAR(255) NOT NULL,
  business_date DATE NOT NULL,
  customer_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  direction VARCHAR(16) NOT NULL,
  loading_place VARCHAR(255) NOT NULL,
  unloading_place VARCHAR(255) NOT NULL,
  carrying_fee DECIMAL(18,2) NULL DEFAULT 0,
  mileage_km DECIMAL(18,2) NULL,
  tax_unit_price DECIMAL(18,4) NOT NULL,
  info_fee DECIMAL(18,2) NULL DEFAULT 0,
  assigned_driver_id BIGINT NULL,
  import_vehicle_id BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED',
  departure_driver_id BIGINT NULL,
  departure_vehicle_id BIGINT NULL,
  departure_trailer_id BIGINT NULL,
  departure_time TIMESTAMP NULL,
  unload_time TIMESTAMP NULL,
  effective_weight_version_id BIGINT NULL,
  driver_salary DECIMAL(18,2) NULL,
  salary_source VARCHAR(32) NULL,
  cancel_reason VARCHAR(500) NULL,
  void_reason VARCHAR(500) NULL,
  version INT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  UNIQUE (route_no)
);

CREATE TABLE route_status_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  route_id BIGINT NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  operation_type VARCHAR(32) NOT NULL,
  operator_id BIGINT NOT NULL,
  operation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reason VARCHAR(500) NULL
);

CREATE TABLE route_weight_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  route_id BIGINT NOT NULL,
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
  UNIQUE (route_id, version_no)
);

CREATE TABLE driver_salary_entry (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  route_id BIGINT NOT NULL,
  driver_id BIGINT NOT NULL,
  business_month CHAR(7) NOT NULL,
  business_date DATE NOT NULL,
  salary_amount DECIMAL(18,2) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  import_task_id BIGINT NULL,
  import_row_id BIGINT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (route_id)
);

CREATE TABLE driver_salary_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  salary_entry_id BIGINT NULL,
  route_id BIGINT NOT NULL,
  driver_id BIGINT NOT NULL,
  before_amount DECIMAL(18,2) NULL,
  after_amount DECIMAL(18,2) NOT NULL,
  before_source_type VARCHAR(32) NULL,
  after_source_type VARCHAR(32) NOT NULL,
  operator_id BIGINT NOT NULL,
  operation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reason VARCHAR(500) NULL,
  import_task_id BIGINT NULL,
  import_row_id BIGINT NULL
);

CREATE TABLE expense_entry (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  expense_no VARCHAR(64) NOT NULL,
  expense_type VARCHAR(32) NOT NULL,
  business_date DATE NOT NULL,
  vehicle_id BIGINT NOT NULL,
  driver_id BIGINT NULL,
  attribution_type VARCHAR(16) NOT NULL,
  route_id BIGINT NULL,
  amount DECIMAL(18,2) NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  approval_instance_id BIGINT NULL,
  import_row_id BIGINT NULL,
  reversal_of_id BIGINT NULL,
  remark VARCHAR(1000) NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  UNIQUE (expense_no)
);

CREATE TABLE expense_penalty_detail (
  expense_id BIGINT PRIMARY KEY,
  detail VARCHAR(1000) NULL,
  deduct_points DECIMAL(8,4) NULL,
  penalty_no VARCHAR(128) NULL,
  driver_id BIGINT NULL
);

CREATE TABLE expense_repair_detail (
  expense_id BIGINT PRIMARY KEY,
  trailer_id BIGINT NULL,
  detail VARCHAR(1000) NULL,
  receipt_no VARCHAR(128) NULL,
  invoice_no VARCHAR(128) NULL,
  repair_shop VARCHAR(255) NULL
);

CREATE TABLE expense_toll_detail (
  expense_id BIGINT PRIMARY KEY,
  trailer_id BIGINT NULL,
  detail VARCHAR(1000) NULL,
  receipt_no VARCHAR(128) NULL
);

CREATE TABLE expense_attribution_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  expense_id BIGINT NOT NULL,
  before_attribution_type VARCHAR(16) NOT NULL,
  before_route_id BIGINT NULL,
  before_status VARCHAR(32) NOT NULL,
  after_attribution_type VARCHAR(16) NOT NULL,
  after_route_id BIGINT NULL,
  after_status VARCHAR(32) NOT NULL,
  operator_id BIGINT NOT NULL,
  operation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reason VARCHAR(500) NOT NULL
);

CREATE TABLE expense_energy_detail (
  expense_id BIGINT PRIMARY KEY,
  energy_type VARCHAR(16) NOT NULL,
  station_id BIGINT NULL,
  order_no VARCHAR(128) NOT NULL,
  start_time TIMESTAMP NOT NULL,
  quantity DECIMAL(18,3) NOT NULL,
  auto_matched_route_id BIGINT NULL,
  match_status VARCHAR(32) NOT NULL
);

CREATE TABLE expense_reversal_link (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  original_expense_id BIGINT NOT NULL,
  reversal_expense_id BIGINT NOT NULL,
  replacement_expense_id BIGINT NULL,
  reason VARCHAR(500) NOT NULL,
  operator_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (original_expense_id),
  UNIQUE (reversal_expense_id)
);

CREATE TABLE import_template (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_name VARCHAR(128) NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  station_id BIGINT NULL,
  status TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE import_field_mapping (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_id BIGINT NOT NULL,
  source_column VARCHAR(128) NOT NULL,
  target_field VARCHAR(128) NOT NULL,
  transform_rule VARCHAR(500) NULL,
  required_flag TINYINT NOT NULL DEFAULT 0,
  UNIQUE (template_id, source_column)
);

CREATE TABLE import_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_no VARCHAR(64) NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  template_id BIGINT NULL,
  original_file_id BIGINT NOT NULL,
  total_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  unpublished_count INT NOT NULL DEFAULT 0,
  failure_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  uploaded_by BIGINT NOT NULL,
  uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  UNIQUE (batch_no)
);

CREATE TABLE import_row (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  import_task_id BIGINT NOT NULL,
  row_no INT NOT NULL,
  raw_data_json VARCHAR(4000) NOT NULL,
  normalized_data_json VARCHAR(4000) NULL,
  preview_status VARCHAR(32) NULL,
  preview_error_code VARCHAR(64) NULL,
  preview_error_message VARCHAR(1000) NULL,
  final_status VARCHAR(32) NULL,
  final_error_code VARCHAR(64) NULL,
  final_error_message VARCHAR(1000) NULL,
  business_type VARCHAR(32) NULL,
  business_id BIGINT NULL,
  business_unique_key VARCHAR(255) NULL,
  UNIQUE (import_task_id, row_no)
);

CREATE TABLE approval_position (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  position_code VARCHAR(64) NOT NULL,
  position_name VARCHAR(128) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  UNIQUE (position_code)
);

CREATE TABLE approval_flow (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  approval_type VARCHAR(32) NOT NULL,
  flow_name VARCHAR(128) NOT NULL,
  version_no INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP NULL,
  UNIQUE (approval_type, version_no)
);

CREATE TABLE approval_flow_node (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  flow_id BIGINT NOT NULL,
  node_order INT NOT NULL,
  node_name VARCHAR(128) NOT NULL,
  position_id BIGINT NULL,
  approver_user_id BIGINT NOT NULL,
  allow_return TINYINT NOT NULL DEFAULT 1,
  UNIQUE (flow_id, node_order)
);

CREATE TABLE approval_instance (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  approval_no VARCHAR(64) NOT NULL,
  approval_type VARCHAR(32) NOT NULL,
  flow_id BIGINT NOT NULL,
  flow_version INT NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  business_id BIGINT NULL,
  applicant_user_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  current_node_order INT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  UNIQUE (approval_no)
);

CREATE TABLE approval_submission_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  approval_instance_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  business_snapshot_json VARCHAR(4000) NOT NULL,
  submitted_by BIGINT NOT NULL,
  submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (approval_instance_id, version_no)
);

CREATE TABLE approval_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  approval_instance_id BIGINT NOT NULL,
  submission_version_id BIGINT NOT NULL,
  node_id BIGINT NOT NULL,
  node_order INT NOT NULL,
  approver_user_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL
);

CREATE TABLE approval_action (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  operator_id BIGINT NOT NULL,
  comment VARCHAR(2000) NULL,
  target_node_order INT NULL,
  operated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE file_attachment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
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

CREATE TABLE ocr_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tire_no VARCHAR(128) NOT NULL,
  barcode VARCHAR(128) NULL,
  arrival_time TIMESTAMP NULL,
  description VARCHAR(1000) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'IN_STOCK',
  data_source VARCHAR(16) NOT NULL,
  install_time TIMESTAMP NULL,
  vehicle_id BIGINT NULL,
  driver_id BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  UNIQUE (tire_no)
);

CREATE TABLE tire_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  driver_id BIGINT NOT NULL,
  vehicle_id BIGINT NOT NULL,
  approval_instance_id BIGINT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tire_request_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id BIGINT NOT NULL,
  tire_id BIGINT NOT NULL,
  confirmed_tire_no VARCHAR(128) NOT NULL,
  ocr_record_id BIGINT NULL,
  UNIQUE (request_id, tire_id)
);

CREATE TABLE tire_claim (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tire_id BIGINT NOT NULL,
  driver_id BIGINT NOT NULL,
  vehicle_id BIGINT NOT NULL,
  request_id BIGINT NULL,
  install_time TIMESTAMP NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  UNIQUE (tire_id)
);

CREATE TABLE settlement_month (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  year_month CHAR(7) NOT NULL,
  latest_version_no INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (year_month)
);

CREATE TABLE settlement_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  settlement_month_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  total_income DECIMAL(18,2) NOT NULL DEFAULT 0,
  route_expense DECIMAL(18,2) NOT NULL DEFAULT 0,
  daily_expense DECIMAL(18,2) NOT NULL DEFAULT 0,
  driver_salary DECIMAL(18,2) NOT NULL DEFAULT 0,
  total_profit DECIMAL(18,2) NOT NULL DEFAULT 0,
  generated_by BIGINT NOT NULL,
  generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (settlement_month_id, version_no)
);

CREATE TABLE settlement_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  settlement_version_id BIGINT NOT NULL,
  fact_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  snapshot_json VARCHAR(4000) NOT NULL,
  income_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  expense_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  profit_amount DECIMAL(18,2) NOT NULL DEFAULT 0
);

CREATE TABLE settlement_diff (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  settlement_version_id BIGINT NOT NULL,
  previous_version_id BIGINT NULL,
  fact_type VARCHAR(32) NOT NULL,
  source_id BIGINT NULL,
  change_type VARCHAR(32) NOT NULL,
  before_snapshot_json VARCHAR(4000) NULL,
  after_snapshot_json VARCHAR(4000) NULL,
  income_delta DECIMAL(18,2) NOT NULL DEFAULT 0,
  expense_delta DECIMAL(18,2) NOT NULL DEFAULT 0,
  profit_delta DECIMAL(18,2) NOT NULL DEFAULT 0,
  business_operator_id BIGINT NULL,
  business_operated_at TIMESTAMP NULL,
  business_reason VARCHAR(500) NULL
);

CREATE TABLE audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
  terminal VARCHAR(128) NULL
);
