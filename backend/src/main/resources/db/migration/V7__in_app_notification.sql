-- In-app notifications are derived from committed business events only.
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
