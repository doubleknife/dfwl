-- A ticket is uploaded against a route first. Only a successful unload consumes it.
-- The unique attachment key prevents a ticket from being reused by another unload.
CREATE TABLE route_unload_evidence (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  route_id BIGINT UNSIGNED NOT NULL,
  participant_id BIGINT UNSIGNED NOT NULL,
  weight_version_id BIGINT UNSIGNED NOT NULL,
  attachment_id BIGINT UNSIGNED NOT NULL,
  uploaded_by BIGINT UNSIGNED NOT NULL,
  bound_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_route_unload_attachment (attachment_id),
  KEY idx_route_unload_route (route_id, weight_version_id),
  KEY idx_route_unload_participant (participant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
