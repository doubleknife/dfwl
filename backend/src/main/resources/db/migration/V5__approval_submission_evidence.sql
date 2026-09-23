-- A submission's evidence is a versioned reference to the original attachment, never a file copy.
CREATE TABLE approval_submission_attachment (
  submission_version_id BIGINT UNSIGNED NOT NULL,
  attachment_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (submission_version_id, attachment_id),
  KEY idx_approval_submission_attachment_file (attachment_id),
  CONSTRAINT fk_submission_evidence_version FOREIGN KEY (submission_version_id)
    REFERENCES approval_submission_version (id),
  CONSTRAINT fk_submission_evidence_file FOREIGN KEY (attachment_id)
    REFERENCES file_attachment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Previous application evidence can be identified only when both the frozen snapshot
-- and the bound attachment agree on the same approval instance.
INSERT INTO approval_submission_attachment (submission_version_id, attachment_id)
SELECT DISTINCT s.id, f.id
FROM approval_submission_version s
JOIN JSON_TABLE(s.business_snapshot_json, '$.attachmentIds[*]'
  COLUMNS (attachment_id BIGINT PATH '$')) snapshot_file
JOIN file_attachment f ON f.id = snapshot_file.attachment_id
JOIN approval_instance i ON i.id = s.approval_instance_id
WHERE f.owner_type = 'APPROVAL'
  AND f.owner_id = i.id
  AND f.purpose = 'APPROVAL_APPLICATION';

-- Historical tire images are recoverable only when the frozen item, tire request,
-- OCR record and original uploader all identify the same image.
INSERT INTO approval_submission_attachment (submission_version_id, attachment_id)
SELECT DISTINCT s.id, f.id
FROM approval_submission_version s
JOIN approval_instance i ON i.id = s.approval_instance_id AND i.business_type = 'TIRE_REQUEST'
JOIN tire_request r ON r.id = i.business_id AND r.approval_instance_id = i.id
JOIN JSON_TABLE(s.business_snapshot_json, '$.items[*]'
  COLUMNS (
    tire_id BIGINT PATH '$.tireId',
    ocr_record_id BIGINT PATH '$.ocrRecordId',
    ocr_attachment_id BIGINT PATH '$.ocrAttachmentId',
    confirmed_tire_no VARCHAR(128) PATH '$.confirmedTireNo'
  )) snapshot_item
JOIN tire_request_item ri ON ri.request_id = r.id
  AND ri.tire_id = snapshot_item.tire_id
  AND ri.ocr_record_id = snapshot_item.ocr_record_id
  AND ri.confirmed_tire_no = snapshot_item.confirmed_tire_no
JOIN ocr_record o ON o.id = ri.ocr_record_id AND o.attachment_id = snapshot_item.ocr_attachment_id
JOIN file_attachment f ON f.id = o.attachment_id
  AND f.purpose = 'TIRE_OCR'
  AND f.uploaded_by = i.applicant_user_id;
