package com.dfwl.fleet.approval.repository;

import com.dfwl.fleet.approval.dto.response.ApprovalResponse;
import com.dfwl.fleet.approval.dto.response.ApprovalDetailResponse;
import com.dfwl.fleet.approval.dto.response.ApprovalFlowDetailResponse;
import com.dfwl.fleet.approval.dto.response.ApprovalFlowNodeResponse;
import com.dfwl.fleet.approval.dto.response.ApprovalFlowResponse;
import com.dfwl.fleet.approval.dto.request.ApprovalFlowUpdateRequest;
import com.dfwl.fleet.approval.dto.query.ApprovalListScope;
import com.dfwl.fleet.attachment.dto.response.AttachmentResponse;
import com.dfwl.fleet.common.api.PageResponse;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalRepository {

    private final JdbcTemplate jdbcTemplate;

    public ApprovalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<FlowRecord> activeFlow(String approvalType) {
        return jdbcTemplate.query("""
                SELECT * FROM approval_flow
                WHERE approval_type = ? AND status = 'ACTIVE'
                ORDER BY version_no DESC LIMIT 1
                """, (rs, rowNum) -> new FlowRecord(
                rs.getLong("id"),
                rs.getString("approval_type"),
                rs.getInt("version_no"),
                rs.getString("status")), approvalType).stream().findFirst();
    }

    public Optional<FlowRecord> findFlow(long flowId) {
        return jdbcTemplate.query("""
                SELECT * FROM approval_flow WHERE id = ?
                """, (rs, rowNum) -> new FlowRecord(
                rs.getLong("id"),
                rs.getString("approval_type"),
                rs.getInt("version_no"),
                rs.getString("status")), flowId).stream().findFirst();
    }

    public Optional<ApprovalFlowResponse> flowResponse(long flowId) {
        return jdbcTemplate.query("""
                SELECT id, approval_type, flow_name, version_no, status
                FROM approval_flow WHERE id = ?
                """, (rs, rowNum) -> new ApprovalFlowResponse(
                rs.getLong("id"),
                rs.getString("approval_type"),
                rs.getString("flow_name"),
                rs.getInt("version_no"),
                rs.getString("status")), flowId).stream().findFirst();
    }

    public PageResponse<ApprovalFlowResponse> listFlows(String approvalType, String status, int pageNo, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (approvalType != null && !approvalType.isBlank()) {
            where.append(" AND approval_type = ?");
            args.add(approvalType);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(status);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM approval_flow" + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageSize);
        queryArgs.add(Math.max(pageNo - 1, 0) * pageSize);
        List<ApprovalFlowResponse> records = jdbcTemplate.query("""
                SELECT id, approval_type, flow_name, version_no, status
                FROM approval_flow
                %s
                ORDER BY approval_type, version_no DESC, id DESC
                LIMIT ? OFFSET ?
                """.formatted(where), this::mapFlowResponse, queryArgs.toArray());
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public List<ApprovalFlowResponse> flowVersions(String approvalType) {
        return jdbcTemplate.query("""
                SELECT id, approval_type, flow_name, version_no, status
                FROM approval_flow
                WHERE approval_type = ?
                ORDER BY version_no DESC, id DESC
                """, this::mapFlowResponse, approvalType);
    }

    public Optional<ApprovalFlowDetailResponse> activeFlowDetail(String approvalType) {
        return jdbcTemplate.query("""
                SELECT id, approval_type, flow_name, version_no, status
                FROM approval_flow
                WHERE approval_type = ? AND status = 'ACTIVE'
                ORDER BY version_no DESC LIMIT 1
                """, this::mapFlowResponse, approvalType).stream()
                .findFirst()
                .map(flow -> new ApprovalFlowDetailResponse(flow, flowNodes(flow.id())));
    }

    public Optional<ApprovalFlowDetailResponse> flowDetail(long flowId) {
        return flowResponse(flowId).map(flow -> new ApprovalFlowDetailResponse(flow, flowNodes(flow.id())));
    }

    public List<ApprovalFlowNodeResponse> flowNodes(long flowId) {
        return jdbcTemplate.query("""
                SELECT id, flow_id, node_order, node_name, position_id, approver_user_id, allow_return
                FROM approval_flow_node
                WHERE flow_id = ?
                ORDER BY node_order
                """, (rs, rowNum) -> new ApprovalFlowNodeResponse(
                rs.getLong("id"),
                rs.getLong("flow_id"),
                rs.getInt("node_order"),
                rs.getString("node_name"),
                readLong(rs, "position_id"),
                rs.getLong("approver_user_id"),
                rs.getInt("allow_return") == 1), flowId);
    }

    public boolean flowHasNodes(long flowId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM approval_flow_node WHERE flow_id = ?", Integer.class, flowId);
        return count != null && count > 0;
    }

    public Optional<FlowNodeRecord> firstNode(long flowId) {
        return nodeByOrder(flowId, 1);
    }

    public Optional<FlowNodeRecord> nodeByOrder(long flowId, int nodeOrder) {
        return jdbcTemplate.query("""
                SELECT * FROM approval_flow_node
                WHERE flow_id = ? AND node_order = ?
                """, (rs, rowNum) -> new FlowNodeRecord(
                rs.getLong("id"),
                rs.getLong("flow_id"),
                rs.getInt("node_order"),
                rs.getLong("approver_user_id"),
                rs.getInt("allow_return") == 1), flowId, nodeOrder).stream().findFirst();
    }

    public Optional<FlowNodeRecord> nextNode(long flowId, int currentOrder) {
        return jdbcTemplate.query("""
                SELECT * FROM approval_flow_node
                WHERE flow_id = ? AND node_order > ?
                ORDER BY node_order LIMIT 1
                """, (rs, rowNum) -> new FlowNodeRecord(
                rs.getLong("id"),
                rs.getLong("flow_id"),
                rs.getInt("node_order"),
                rs.getLong("approver_user_id"),
                rs.getInt("allow_return") == 1), flowId, currentOrder).stream().findFirst();
    }

    public long createInstance(String approvalNo, FlowRecord flow, String businessType, Long businessId, long applicantUserId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO approval_instance (approval_no, approval_type, flow_id, flow_version, business_type,
                                                   business_id, applicant_user_id, status, current_node_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', 1)
                    """, new String[]{"id"});
            ps.setString(1, approvalNo);
            ps.setString(2, flow.approvalType());
            ps.setLong(3, flow.id());
            ps.setInt(4, flow.versionNo());
            ps.setString(5, businessType);
            ps.setObject(6, businessId);
            ps.setLong(7, applicantUserId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public long createSubmission(long instanceId, int versionNo, String snapshotJson, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO approval_submission_version (approval_instance_id, version_no, business_snapshot_json, submitted_by)
                    VALUES (?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, instanceId);
            ps.setInt(2, versionNo);
            ps.setString(3, snapshotJson);
            ps.setLong(4, operatorId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void createTask(long instanceId, long submissionVersionId, FlowNodeRecord node) {
        jdbcTemplate.update("""
                INSERT INTO approval_task (approval_instance_id, submission_version_id, node_id, node_order, approver_user_id)
                VALUES (?, ?, ?, ?, ?)
                """, instanceId, submissionVersionId, node.id(), node.nodeOrder(), node.approverUserId());
    }

    public long createFlowVersion(String approvalType, String flowName, int versionNo, long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO approval_flow (approval_type, flow_name, version_no, status, created_by)
                    VALUES (?, ?, ?, 'DRAFT', ?)
                    """, new String[]{"id"});
            ps.setString(1, approvalType);
            ps.setString(2, flowName);
            ps.setInt(3, versionNo);
            ps.setLong(4, operatorId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void createFlowNode(long flowId, ApprovalFlowUpdateRequest.Node node) {
        jdbcTemplate.update("""
                INSERT INTO approval_flow_node (flow_id, node_order, node_name, position_id, approver_user_id, allow_return)
                VALUES (?, ?, ?, ?, ?, ?)
                """, flowId, node.nodeOrder(), node.nodeName(), node.positionId(), node.approverUserId(),
                Boolean.FALSE.equals(node.allowReturn()) ? 0 : 1);
    }

    public Optional<ApprovalResponse> find(long id) {
        return jdbcTemplate.query("SELECT * FROM approval_instance WHERE id = ?", this::mapApproval, id).stream().findFirst();
    }

    public Optional<SubmissionRecord> findSubmission(long id) {
        return jdbcTemplate.query("""
                SELECT *
                FROM approval_submission_version
                WHERE id = ?
                """, (rs, rowNum) -> new SubmissionRecord(
                rs.getLong("id"),
                rs.getLong("approval_instance_id"),
                rs.getInt("version_no"),
                rs.getString("business_snapshot_json"),
                rs.getLong("submitted_by"),
                rs.getTimestamp("submitted_at").toLocalDateTime()), id).stream().findFirst();
    }

    public PageResponse<ApprovalResponse> list(int pageNo, int pageSize) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM approval_instance", Long.class);
        List<ApprovalResponse> records = jdbcTemplate.query("""
                SELECT * FROM approval_instance ORDER BY id DESC LIMIT ? OFFSET ?
                """, this::mapApproval, pageSize, Math.max(pageNo - 1, 0) * pageSize);
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public PageResponse<ApprovalResponse> list(ApprovalListScope scope, String approvalType, String status,
                                               long currentUserId, int pageNo, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (approvalType != null && !approvalType.isBlank()) {
            where.append(" AND i.approval_type = ?");
            args.add(approvalType);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND i.status = ?");
            args.add(status);
        }
        switch (scope) {
            case TODO -> {
                where.append("""
                         AND EXISTS (
                           SELECT 1 FROM approval_task t
                           WHERE t.approval_instance_id = i.id
                             AND t.approver_user_id = ?
                             AND t.status = 'PENDING'
                         )
                        """);
                args.add(currentUserId);
            }
            case DONE -> {
                where.append("""
                         AND EXISTS (
                           SELECT 1 FROM approval_task t
                           JOIN approval_action a ON a.task_id = t.id
                           WHERE t.approval_instance_id = i.id
                             AND a.operator_id = ?
                         )
                        """);
                args.add(currentUserId);
            }
            case MINE -> {
                where.append(" AND i.applicant_user_id = ?");
                args.add(currentUserId);
            }
            case ALL -> {
            }
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM approval_instance i" + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageSize);
        queryArgs.add(Math.max(pageNo - 1, 0) * pageSize);
        List<ApprovalResponse> records = jdbcTemplate.query("""
                SELECT i.*
                FROM approval_instance i
                %s
                ORDER BY i.id DESC
                LIMIT ? OFFSET ?
                """.formatted(where), this::mapApproval, queryArgs.toArray());
        return new PageResponse<>(pageNo, pageSize, total == null ? 0 : total, records);
    }

    public Optional<TaskRecord> pendingTask(long instanceId) {
        return jdbcTemplate.query("""
                SELECT t.*
                FROM approval_task t
                JOIN approval_instance i ON i.id = t.approval_instance_id
                WHERE t.approval_instance_id = ?
                  AND t.status = 'PENDING'
                  AND i.status = 'PENDING'
                  AND i.current_node_order = t.node_order
                  AND t.submission_version_id = (
                    SELECT MAX(id) FROM approval_submission_version WHERE approval_instance_id = ?
                  )
                ORDER BY t.id DESC LIMIT 1
                """, (rs, rowNum) -> new TaskRecord(
                rs.getLong("id"),
                rs.getLong("approval_instance_id"),
                rs.getLong("submission_version_id"),
                rs.getInt("node_order"),
                rs.getLong("approver_user_id")), instanceId, instanceId).stream().findFirst();
    }

    public boolean finishTask(long taskId, String status) {
        return jdbcTemplate.update("""
                UPDATE approval_task
                SET status = ?, completed_at = ?
                WHERE id = ? AND status = 'PENDING'
                """, status, Timestamp.valueOf(LocalDateTime.now()), taskId) == 1;
    }

    public long insertAction(long taskId, String actionType, long operatorId, String comment, Integer targetNodeOrder) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO approval_action (task_id, action_type, operator_id, comment, target_node_order)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, taskId);
            ps.setString(2, actionType);
            ps.setLong(3, operatorId);
            ps.setString(4, comment);
            ps.setObject(5, targetNodeOrder);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int invalidatePendingTasks(long instanceId) {
        return jdbcTemplate.update("""
                UPDATE approval_task
                SET status = 'INVALIDATED', completed_at = ?
                WHERE approval_instance_id = ? AND status = 'PENDING'
                """, Timestamp.valueOf(LocalDateTime.now()), instanceId);
    }

    public int invalidatePendingTasksForSubmission(long instanceId, long submissionVersionId) {
        return jdbcTemplate.update("""
                UPDATE approval_task
                SET status = 'INVALIDATED', completed_at = ?
                WHERE approval_instance_id = ? AND submission_version_id = ? AND status = 'PENDING'
                """, Timestamp.valueOf(LocalDateTime.now()), instanceId, submissionVersionId);
    }

    public void moveToNode(long instanceId, int nodeOrder) {
        jdbcTemplate.update("UPDATE approval_instance SET status = 'PENDING', current_node_order = ? WHERE id = ?", nodeOrder, instanceId);
    }

    public void complete(long instanceId) {
        jdbcTemplate.update("UPDATE approval_instance SET status = 'APPROVED', current_node_order = NULL, completed_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), instanceId);
    }

    public void returnApplicant(long instanceId) {
        jdbcTemplate.update("UPDATE approval_instance SET status = 'RETURNED_TO_APPLICANT', current_node_order = NULL WHERE id = ?", instanceId);
    }

    public int nextSubmissionVersion(long instanceId) {
        Integer max = jdbcTemplate.queryForObject("SELECT MAX(version_no) FROM approval_submission_version WHERE approval_instance_id = ?", Integer.class, instanceId);
        return max == null ? 1 : max + 1;
    }

    public boolean hasInProgressForFlow(long flowId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM approval_instance
                WHERE flow_id = ? AND status IN ('PENDING', 'RETURNED_TO_APPLICANT')
                """, Integer.class, flowId);
        return count != null && count > 0;
    }

    public boolean hasOpenInstancesForApprovalType(String approvalType) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM approval_instance
                WHERE approval_type = ? AND status IN ('PENDING', 'RETURNED_TO_APPLICANT')
                """, Integer.class, approvalType);
        return count != null && count > 0;
    }

    public int nextFlowVersion(String approvalType) {
        Integer max = jdbcTemplate.queryForObject("SELECT MAX(version_no) FROM approval_flow WHERE approval_type = ?", Integer.class, approvalType);
        return max == null ? 1 : max + 1;
    }

    public void updateFlowStatus(long flowId, String status) {
        jdbcTemplate.update("UPDATE approval_flow SET status = ?, published_at = CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP ELSE published_at END WHERE id = ?",
                status, status, flowId);
    }

    public Optional<ApprovalDetailResponse> detail(long instanceId) {
        Optional<ApprovalResponse> approval = find(instanceId);
        if (approval.isEmpty()) {
            return Optional.empty();
        }
        List<MutableSubmissionVersion> rawSubmissions = jdbcTemplate.query("""
                SELECT *
                FROM approval_submission_version
                WHERE approval_instance_id = ?
                ORDER BY version_no
                """, (rs, rowNum) -> new MutableSubmissionVersion(
                rs.getLong("id"),
                rs.getInt("version_no"),
                rs.getString("business_snapshot_json"),
                rs.getLong("submitted_by"),
                rs.getTimestamp("submitted_at").toLocalDateTime()), instanceId);
        Map<Long, AttachmentResponse> approvalAttachments = attachmentsForApprovalInstance(instanceId);
        List<ApprovalDetailResponse.SubmissionVersion> submissions = rawSubmissions.stream()
                .map(submission -> new ApprovalDetailResponse.SubmissionVersion(
                        submission.id(), submission.versionNo(), submission.businessSnapshotJson(),
                        submission.submittedBy(), submission.submittedAt(),
                        attachmentIdsFromSnapshot(submission.businessSnapshotJson()).stream()
                                .map(approvalAttachments::get)
                                .filter(java.util.Objects::nonNull)
                                .toList()))
                .toList();
        Map<Long, MutableTaskHistory> tasks = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT t.*, s.version_no AS submission_version_no, n.node_name
                FROM approval_task t
                JOIN approval_submission_version s ON s.id = t.submission_version_id
                JOIN approval_flow_node n ON n.id = t.node_id
                WHERE t.approval_instance_id = ?
                ORDER BY s.version_no, t.id
                """, rs -> {
            long taskId = rs.getLong("id");
            tasks.put(taskId, new MutableTaskHistory(
                    taskId,
                    rs.getLong("submission_version_id"),
                    rs.getInt("submission_version_no"),
                    rs.getInt("node_order"),
                    rs.getString("node_name"),
                    rs.getLong("approver_user_id"),
                    rs.getString("status"),
                    "INVALIDATED".equals(rs.getString("status")),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime(),
                    new ArrayList<>()));
        }, instanceId);
        List<Long> actionIds = new ArrayList<>();
        if (!tasks.isEmpty()) {
            String placeholders = String.join(",", tasks.keySet().stream().map(id -> "?").toList());
            List<Object> args = new ArrayList<>(tasks.keySet());
            jdbcTemplate.query("""
                    SELECT *
                    FROM approval_action
                    WHERE task_id IN (%s)
                    ORDER BY operated_at, id
                    """.formatted(placeholders), rs -> {
                MutableTaskHistory task = tasks.get(rs.getLong("task_id"));
                if (task != null) {
                    long actionId = rs.getLong("id");
                    actionIds.add(actionId);
                    task.actions().add(new MutableActionHistory(
                            actionId,
                            rs.getString("action_type"),
                            rs.getLong("operator_id"),
                            rs.getString("comment"),
                            readInteger(rs, "target_node_order"),
                            rs.getTimestamp("operated_at").toLocalDateTime()));
                }
            }, args.toArray());
        }
        Map<Long, List<AttachmentResponse>> actionAttachments = attachmentsByOwner("APPROVAL_ACTION", actionIds);
        List<ApprovalDetailResponse.TaskHistory> histories = tasks.values().stream()
                .map(task -> new ApprovalDetailResponse.TaskHistory(
                        task.id(), task.submissionVersionId(), task.submissionVersionNo(), task.nodeOrder(),
                        task.nodeName(), task.approverUserId(), task.status(), task.invalidated(),
                        task.createdAt(), task.completedAt(), task.actions().stream()
                        .map(action -> new ApprovalDetailResponse.ActionHistory(
                                action.id(), action.actionType(), action.operatorId(), action.comment(),
                                action.targetNodeOrder(), action.operatedAt(),
                                actionAttachments.getOrDefault(action.id(), List.of())))
                        .toList()))
                .toList();
        return Optional.of(new ApprovalDetailResponse(approval.get(), submissions, histories));
    }

    private Map<Long, List<AttachmentResponse>> attachmentsByOwner(String ownerType, List<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", ownerIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(ownerType);
        args.addAll(ownerIds);
        Map<Long, List<AttachmentResponse>> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT id, owner_type, owner_id, purpose, storage_key, original_filename,
                       content_type, file_size, file_hash, uploaded_by, uploaded_at
                FROM file_attachment
                WHERE owner_type = ?
                  AND owner_id IN (%s)
                ORDER BY id
                """.formatted(placeholders), rs -> {
            long ownerId = rs.getLong("owner_id");
            result.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(new AttachmentResponse(
                    rs.getLong("id"),
                    rs.getString("owner_type"),
                    ownerId,
                    rs.getString("purpose"),
                    rs.getString("original_filename"),
                    rs.getString("content_type"),
                    rs.getLong("file_size"),
                    rs.getString("file_hash"),
                    rs.getString("storage_key"),
                    rs.getLong("uploaded_by"),
                    rs.getTimestamp("uploaded_at") == null ? null : rs.getTimestamp("uploaded_at").toLocalDateTime()));
        }, args.toArray());
        return result;
    }

    private Map<Long, AttachmentResponse> attachmentsForApprovalInstance(long approvalInstanceId) {
        Map<Long, AttachmentResponse> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT id, owner_type, owner_id, purpose, storage_key, original_filename,
                       content_type, file_size, file_hash, uploaded_by, uploaded_at
                FROM file_attachment
                WHERE owner_type = 'APPROVAL'
                  AND owner_id = ?
                ORDER BY id
                """, rs -> {
            long id = rs.getLong("id");
            result.put(id, new AttachmentResponse(
                    id,
                    rs.getString("owner_type"),
                    rs.getLong("owner_id"),
                    rs.getString("purpose"),
                    rs.getString("original_filename"),
                    rs.getString("content_type"),
                    rs.getLong("file_size"),
                    rs.getString("file_hash"),
                    rs.getString("storage_key"),
                    rs.getLong("uploaded_by"),
                    rs.getTimestamp("uploaded_at") == null ? null : rs.getTimestamp("uploaded_at").toLocalDateTime()));
        }, approvalInstanceId);
        return result;
    }

    private List<Long> attachmentIdsFromSnapshot(String snapshotJson) {
        if (snapshotJson == null || !snapshotJson.contains("\"attachmentIds\"")) {
            return List.of();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"attachmentIds\"\\s*:\\s*\\[(?<ids>[^]]*)]")
                .matcher(snapshotJson);
        if (!matcher.find()) {
            return List.of();
        }
        String ids = matcher.group("ids").trim();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String id : ids.split(",")) {
            try {
                result.add(Long.parseLong(id.trim()));
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        return result;
    }

    private ApprovalResponse mapApproval(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ApprovalResponse(
                rs.getLong("id"),
                rs.getString("approval_no"),
                rs.getString("approval_type"),
                rs.getLong("flow_id"),
                rs.getInt("flow_version"),
                rs.getString("business_type"),
                readLong(rs, "business_id"),
                rs.getLong("applicant_user_id"),
                rs.getString("status"),
                readInteger(rs, "current_node_order"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime());
    }

    private ApprovalFlowResponse mapFlowResponse(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ApprovalFlowResponse(
                rs.getLong("id"),
                rs.getString("approval_type"),
                rs.getString("flow_name"),
                rs.getInt("version_no"),
                rs.getString("status"));
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer readInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public record FlowRecord(long id, String approvalType, int versionNo, String status) {
    }

    public record FlowNodeRecord(long id, long flowId, int nodeOrder, long approverUserId, boolean allowReturn) {
    }

    public record TaskRecord(long id, long instanceId, long submissionVersionId, int nodeOrder, long approverUserId) {
    }

    public record SubmissionRecord(long id, long approvalInstanceId, int versionNo, String businessSnapshotJson,
                                   long submittedBy, LocalDateTime submittedAt) {
    }

    private record MutableTaskHistory(
            Long id,
            Long submissionVersionId,
            Integer submissionVersionNo,
            Integer nodeOrder,
            String nodeName,
            Long approverUserId,
            String status,
            Boolean invalidated,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            List<MutableActionHistory> actions
    ) {
    }

    private record MutableSubmissionVersion(
            Long id,
            Integer versionNo,
            String businessSnapshotJson,
            Long submittedBy,
            LocalDateTime submittedAt
    ) {
    }

    private record MutableActionHistory(
            Long id,
            String actionType,
            Long operatorId,
            String comment,
            Integer targetNodeOrder,
            LocalDateTime operatedAt
    ) {
    }
}
