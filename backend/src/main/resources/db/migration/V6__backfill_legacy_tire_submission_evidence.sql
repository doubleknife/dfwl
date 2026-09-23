-- Older tire submissions recorded the OCR record but not its attachment ID.
-- Recover only when the frozen item and the original request item agree.
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
JOIN ocr_record o ON o.id = ri.ocr_record_id
  AND o.confirmed_text = ri.confirmed_tire_no
JOIN file_attachment f ON f.id = o.attachment_id
  AND f.owner_type = 'TIRE_OCR'
  AND f.purpose = 'TIRE_OCR'
  AND f.uploaded_by = i.applicant_user_id
WHERE snapshot_item.ocr_attachment_id IS NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(s.business_snapshot_json, '$.requestId')) = CAST(r.id AS CHAR)
  AND NOT EXISTS (
    SELECT 1 FROM approval_submission_attachment existing
    WHERE existing.submission_version_id = s.id AND existing.attachment_id = f.id
  );
