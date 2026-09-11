-- Fleet Operations MySQL 8.0 schema V1.0 development baseline
-- Business enum columns intentionally use VARCHAR and are validated by Java Enum + backend rules.


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

CREATE TABLE customer (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  customer_code VARCHAR(64) NULL,
  customer_name VARCHAR(128) NOT NULL,
  status TINYINT UNSIGNED NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_customer_code (customer_code),
  KEY idx_customer_name_status (customer_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  product_code VARCHAR(64) NULL,
  product_name VARCHAR(128) NOT NULL,
  status TINYINT UNSIGNED NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_product_code (product_code),
  KEY idx_product_name_status (product_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE route_task (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  route_no VARCHAR(64) NOT NULL,
  external_route_no VARCHAR(128) NULL,
  trip_sequence VARCHAR(64) NULL,
  business_unique_key VARCHAR(255) NOT NULL,
  business_date DATE NOT NULL,
  customer_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  direction VARCHAR(16) NOT NULL,
  loading_place VARCHAR(255) NOT NULL,
  unloading_place VARCHAR(255) NOT NULL,
  carrying_fee DECIMAL(18,2) NULL DEFAULT 0,
  mileage_km DECIMAL(18,2) NULL,
  tax_unit_price DECIMAL(18,4) NOT NULL,
  info_fee DECIMAL(18,2) NULL DEFAULT 0,
  assigned_driver_id BIGINT UNSIGNED NULL,
  import_vehicle_id BIGINT UNSIGNED NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED',
  departure_driver_id BIGINT UNSIGNED NULL,
  departure_vehicle_id BIGINT UNSIGNED NULL,
  departure_trailer_id BIGINT UNSIGNED NULL,
  departure_time DATETIME(3) NULL,
  unload_time DATETIME(3) NULL,
  effective_weight_version_id BIGINT UNSIGNED NULL,
  driver_salary DECIMAL(18,2) NULL,
  salary_source VARCHAR(32) NULL,
  cancel_reason VARCHAR(500) NULL,
  void_reason VARCHAR(500) NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT UNSIGNED NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_route_no (route_no),
  KEY idx_route_business_unique_key (business_unique_key),
  KEY idx_route_status_driver_date (status, assigned_driver_id, business_date),
  KEY idx_route_vehicle_time (departure_vehicle_id, departure_time, unload_time),
  KEY idx_route_business_date (business_date, status),
  KEY idx_route_external_trip (external_route_no, trip_sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE route_status_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  route_id BIGINT UNSIGNED NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  operation_type VARCHAR(32) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  operation_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reason VARCHAR(500) NULL,
  KEY idx_route_status_hist (route_id, operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE route_weight_version (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  route_id BIGINT UNSIGNED NOT NULL,
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
  UNIQUE KEY uk_route_weight_ver (route_id, version_no),
  KEY idx_route_weight_route (route_id, operation_time),
  CONSTRAINT chk_route_weight_positive CHECK (gross_weight > 0 AND tare_weight > 0),
  CONSTRAINT chk_route_weight_gt CHECK (gross_weight > tare_weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE driver_salary_entry (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  route_id BIGINT UNSIGNED NOT NULL,
  driver_id BIGINT UNSIGNED NOT NULL,
  business_month CHAR(7) NOT NULL,
  business_date DATE NOT NULL,
  salary_amount DECIMAL(18,2) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  import_task_id BIGINT UNSIGNED NULL,
  import_row_id BIGINT UNSIGNED NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT UNSIGNED NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_driver_salary_route (route_id),
  KEY idx_driver_salary_driver_month (driver_id, business_month),
  KEY idx_driver_salary_import_row (import_row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE driver_salary_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  salary_entry_id BIGINT UNSIGNED NULL,
  route_id BIGINT UNSIGNED NOT NULL,
  driver_id BIGINT UNSIGNED NOT NULL,
  before_amount DECIMAL(18,2) NULL,
  after_amount DECIMAL(18,2) NOT NULL,
  before_source_type VARCHAR(32) NULL,
  after_source_type VARCHAR(32) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  operation_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reason VARCHAR(500) NULL,
  import_task_id BIGINT UNSIGNED NULL,
  import_row_id BIGINT UNSIGNED NULL,
  KEY idx_driver_salary_hist_route (route_id, operation_time),
  KEY idx_driver_salary_hist_entry (salary_entry_id, operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expense_entry (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  expense_no VARCHAR(64) NOT NULL,
  expense_type VARCHAR(32) NOT NULL,
  business_date DATE NOT NULL,
  vehicle_id BIGINT UNSIGNED NOT NULL,
  driver_id BIGINT UNSIGNED NULL,
  attribution_type VARCHAR(16) NOT NULL,
  route_id BIGINT UNSIGNED NULL,
  amount DECIMAL(18,2) NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  approval_instance_id BIGINT UNSIGNED NULL,
  import_row_id BIGINT UNSIGNED NULL,
  reversal_of_id BIGINT UNSIGNED NULL,
  remark VARCHAR(1000) NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT UNSIGNED NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_expense_no (expense_no),
  KEY idx_expense_vehicle_date (vehicle_id, business_date),
  KEY idx_expense_route_type (route_id, expense_type, status),
  KEY idx_expense_date_attr (business_date, attribution_type, status),
  KEY idx_expense_approval (approval_instance_id),
  CONSTRAINT chk_expense_attr CHECK (
    (status = 'PENDING_ATTRIBUTION')
    OR (attribution_type = 'ROUTE' AND route_id IS NOT NULL)
    OR (attribution_type = 'DAILY' AND route_id IS NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expense_penalty_detail (
  expense_id BIGINT UNSIGNED PRIMARY KEY,
  detail VARCHAR(1000) NULL,
  deduct_points DECIMAL(8,4) NULL,
  penalty_no VARCHAR(128) NULL,
  driver_id BIGINT UNSIGNED NULL,
  KEY idx_penalty_no (penalty_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expense_repair_detail (
  expense_id BIGINT UNSIGNED PRIMARY KEY,
  trailer_id BIGINT UNSIGNED NULL,
  detail VARCHAR(1000) NULL,
  receipt_no VARCHAR(128) NULL,
  invoice_no VARCHAR(128) NULL,
  repair_shop VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expense_toll_detail (
  expense_id BIGINT UNSIGNED PRIMARY KEY,
  trailer_id BIGINT UNSIGNED NULL,
  detail VARCHAR(1000) NULL,
  receipt_no VARCHAR(128) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expense_attribution_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  expense_id BIGINT UNSIGNED NOT NULL,
  before_attribution_type VARCHAR(16) NOT NULL,
  before_route_id BIGINT UNSIGNED NULL,
  before_status VARCHAR(32) NOT NULL,
  after_attribution_type VARCHAR(16) NOT NULL,
  after_route_id BIGINT UNSIGNED NULL,
  after_status VARCHAR(32) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  operation_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reason VARCHAR(500) NOT NULL,
  KEY idx_exp_attr_hist_expense (expense_id, operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expense_energy_detail (
  expense_id BIGINT UNSIGNED PRIMARY KEY,
  energy_type VARCHAR(16) NOT NULL,
  station_id BIGINT UNSIGNED NULL,
  order_no VARCHAR(128) NOT NULL,
  start_time DATETIME(3) NOT NULL,
  quantity DECIMAL(18,3) NOT NULL,
  auto_matched_route_id BIGINT UNSIGNED NULL,
  match_status VARCHAR(32) NOT NULL,
  KEY idx_energy_order (energy_type, station_id, order_no),
  KEY idx_energy_start_time (start_time)
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

CREATE TABLE approval_position (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  position_code VARCHAR(64) NOT NULL,
  position_name VARCHAR(128) NOT NULL,
  status TINYINT UNSIGNED NOT NULL DEFAULT 1,
  UNIQUE KEY uk_position_code (position_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_flow (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  approval_type VARCHAR(32) NOT NULL,
  flow_name VARCHAR(128) NOT NULL,
  version_no INT UNSIGNED NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  published_at DATETIME(3) NULL,
  UNIQUE KEY uk_flow_type_version (approval_type, version_no),
  KEY idx_flow_type_status (approval_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_flow_node (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  flow_id BIGINT UNSIGNED NOT NULL,
  node_order INT UNSIGNED NOT NULL,
  node_name VARCHAR(128) NOT NULL,
  position_id BIGINT UNSIGNED NULL,
  approver_user_id BIGINT UNSIGNED NOT NULL,
  allow_return TINYINT UNSIGNED NOT NULL DEFAULT 1,
  UNIQUE KEY uk_flow_node_order (flow_id, node_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_instance (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  approval_no VARCHAR(64) NOT NULL,
  approval_type VARCHAR(32) NOT NULL,
  flow_id BIGINT UNSIGNED NOT NULL,
  flow_version INT UNSIGNED NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  business_id BIGINT UNSIGNED NULL,
  applicant_user_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(32) NOT NULL,
  current_node_order INT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  UNIQUE KEY uk_approval_no (approval_no),
  KEY idx_approval_business (business_type, business_id),
  KEY idx_approval_type_status (approval_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_submission_version (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  approval_instance_id BIGINT UNSIGNED NOT NULL,
  version_no INT UNSIGNED NOT NULL,
  business_snapshot_json JSON NOT NULL,
  submitted_by BIGINT UNSIGNED NOT NULL,
  submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_approval_submit_ver (approval_instance_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_task (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  approval_instance_id BIGINT UNSIGNED NOT NULL,
  submission_version_id BIGINT UNSIGNED NOT NULL,
  node_id BIGINT UNSIGNED NOT NULL,
  node_order INT UNSIGNED NOT NULL,
  approver_user_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  KEY idx_task_approver_status (approver_user_id, status, created_at),
  KEY idx_task_instance (approval_instance_id, node_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_action (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  comment VARCHAR(2000) NULL,
  target_node_order INT UNSIGNED NULL,
  operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_action_task_time (task_id, operated_at)
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

CREATE TABLE ocr_record (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  attachment_id BIGINT UNSIGNED NOT NULL,
  ocr_provider VARCHAR(64) NULL,
  raw_result_json JSON NULL,
  recognized_text VARCHAR(512) NULL,
  confirmed_text VARCHAR(512) NULL,
  confirmed_by BIGINT UNSIGNED NULL,
  confirmed_at DATETIME(3) NULL,
  KEY idx_ocr_attachment (attachment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tire (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  tire_no VARCHAR(128) NOT NULL,
  barcode VARCHAR(128) NULL,
  arrival_time DATETIME(3) NULL,
  description VARCHAR(1000) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'IN_STOCK',
  data_source VARCHAR(16) NOT NULL,
  install_time DATETIME(3) NULL,
  vehicle_id BIGINT UNSIGNED NULL,
  driver_id BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  UNIQUE KEY uk_tire_no (tire_no),
  KEY idx_tire_status_source (status, data_source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tire_request (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  driver_id BIGINT UNSIGNED NOT NULL,
  vehicle_id BIGINT UNSIGNED NOT NULL,
  approval_instance_id BIGINT UNSIGNED NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_tire_request_driver_status (driver_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tire_request_item (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  request_id BIGINT UNSIGNED NOT NULL,
  tire_id BIGINT UNSIGNED NOT NULL,
  confirmed_tire_no VARCHAR(128) NOT NULL,
  ocr_record_id BIGINT UNSIGNED NULL,
  UNIQUE KEY uk_tire_request_item (request_id, tire_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tire_claim (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  tire_id BIGINT UNSIGNED NOT NULL,
  driver_id BIGINT UNSIGNED NOT NULL,
  vehicle_id BIGINT UNSIGNED NOT NULL,
  request_id BIGINT UNSIGNED NULL,
  install_time DATETIME(3) NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  UNIQUE KEY uk_tire_claim_tire (tire_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE import_template (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  template_name VARCHAR(128) NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  station_id BIGINT UNSIGNED NULL,
  status TINYINT UNSIGNED NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE import_field_mapping (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  template_id BIGINT UNSIGNED NOT NULL,
  source_column VARCHAR(128) NOT NULL,
  target_field VARCHAR(128) NOT NULL,
  transform_rule VARCHAR(500) NULL,
  required_flag TINYINT UNSIGNED NOT NULL DEFAULT 0,
  UNIQUE KEY uk_import_mapping (template_id, source_column)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE import_task (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  batch_no VARCHAR(64) NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  template_id BIGINT UNSIGNED NULL,
  original_file_id BIGINT UNSIGNED NOT NULL,
  total_count INT UNSIGNED NOT NULL DEFAULT 0,
  success_count INT UNSIGNED NOT NULL DEFAULT 0,
  unpublished_count INT UNSIGNED NOT NULL DEFAULT 0,
  failure_count INT UNSIGNED NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  uploaded_by BIGINT UNSIGNED NOT NULL,
  uploaded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  UNIQUE KEY uk_import_batch (batch_no),
  KEY idx_import_type_time (business_type, uploaded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE import_row (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  import_task_id BIGINT UNSIGNED NOT NULL,
  row_no INT UNSIGNED NOT NULL,
  raw_data_json JSON NOT NULL,
  normalized_data_json JSON NULL,
  preview_status VARCHAR(32) NULL,
  preview_error_code VARCHAR(64) NULL,
  preview_error_message VARCHAR(1000) NULL,
  final_status VARCHAR(32) NULL,
  final_error_code VARCHAR(64) NULL,
  final_error_message VARCHAR(1000) NULL,
  business_type VARCHAR(32) NULL,
  business_id BIGINT UNSIGNED NULL,
  business_unique_key VARCHAR(255) NULL,
  UNIQUE KEY uk_import_row (import_task_id, row_no),
  KEY idx_import_key (business_type, business_unique_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE settlement_month (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  year_month CHAR(7) NOT NULL,
  latest_version_no INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_settlement_month (year_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE settlement_version (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  settlement_month_id BIGINT UNSIGNED NOT NULL,
  version_no INT UNSIGNED NOT NULL,
  total_income DECIMAL(18,2) NOT NULL DEFAULT 0,
  route_expense DECIMAL(18,2) NOT NULL DEFAULT 0,
  daily_expense DECIMAL(18,2) NOT NULL DEFAULT 0,
  driver_salary DECIMAL(18,2) NOT NULL DEFAULT 0,
  total_profit DECIMAL(18,2) NOT NULL DEFAULT 0,
  generated_by BIGINT UNSIGNED NOT NULL,
  generated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_settlement_version (settlement_month_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE settlement_detail (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  settlement_version_id BIGINT UNSIGNED NOT NULL,
  fact_type VARCHAR(32) NOT NULL,
  source_id BIGINT UNSIGNED NOT NULL,
  snapshot_json JSON NOT NULL,
  income_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  expense_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  profit_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  KEY idx_settle_detail_ver_type (settlement_version_id, fact_type),
  KEY idx_settle_detail_source (fact_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE settlement_diff (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  settlement_version_id BIGINT UNSIGNED NOT NULL,
  previous_version_id BIGINT UNSIGNED NULL,
  fact_type VARCHAR(32) NOT NULL,
  source_id BIGINT UNSIGNED NULL,
  change_type VARCHAR(32) NOT NULL,
  before_snapshot_json JSON NULL,
  after_snapshot_json JSON NULL,
  income_delta DECIMAL(18,2) NOT NULL DEFAULT 0,
  expense_delta DECIMAL(18,2) NOT NULL DEFAULT 0,
  profit_delta DECIMAL(18,2) NOT NULL DEFAULT 0,
  business_operator_id BIGINT UNSIGNED NULL,
  business_operated_at DATETIME(3) NULL,
  business_reason VARCHAR(500) NULL,
  KEY idx_settle_diff_version (settlement_version_id, change_type)
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
  ('route:list', '线路列表', 'API'),
  ('route:create', '线路创建', 'API'),
  ('route:edit', '线路编辑', 'API'),
  ('route:publish', '线路发布', 'API'),
  ('route:cancel', '线路取消', 'API'),
  ('route:delete', '线路删除', 'API'),
  ('route:depart', '线路发车', 'API'),
  ('route:unload', '线路卸货', 'API'),
  ('route:void', '线路作废', 'API'),
  ('route:reactivate', '线路重新启用', 'API'),
  ('route:weight:adjust', '线路重量调整', 'API'),
  ('expense:list', '费用列表', 'API'),
  ('expense:add', '费用新增', 'API'),
  ('expense:edit', '费用编辑', 'API'),
  ('expense:delete', '费用删除', 'API'),
  ('expense:attribution:edit', '费用归属调整', 'API'),
  ('expense:reversal', '费用冲销', 'API'),
  ('expense:approval:view', '费用审批链查看', 'API'),
  ('approval:create', '审批发起', 'API'),
  ('approval:process', '审批处理', 'API'),
  ('approval:return', '审批打回', 'API'),
  ('approval:history:view', '审批历史查看', 'API'),
  ('approval:flow:manage', '审批流程管理', 'API'),
  ('tire:list', '轮胎列表', 'API'),
  ('tire:import', '轮胎导入', 'API'),
  ('tire:request', '换胎申请', 'API'),
  ('tire:approval:view', '轮胎审批查看', 'API'),
  ('import:preview', '导入预览', 'API'),
  ('import:commit', '导入提交', 'API'),
  ('import:history', '导入历史', 'API'),
  ('import:failure:export', '失败明细导出', 'API'),
  ('report:dashboard', '经营看板', 'API'),
  ('report:profit', '利润报表', 'API'),
  ('report:vehicle', '车辆支出报表', 'API'),
  ('report:driver', '司机支出报表', 'API'),
  ('report:attendance', '出勤报表', 'API'),
  ('report:energy', '能源报表', 'API'),
  ('settlement:view', '结算查看', 'API'),
  ('settlement:generate', '结算生成', 'API'),
  ('settlement:diff:view', '结算差异查看', 'API'),
  ('user:manage', '用户管理', 'API'),
  ('role:manage', '角色管理', 'API'),
  ('permission:manage', '权限管理', 'API'),
  ('audit:view', '审计查看', 'API');
