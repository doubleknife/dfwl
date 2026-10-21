package com.dfwl.fleet.tire.v3;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.CurrentUserService;
import com.dfwl.fleet.system.service.AuditWriteService;
import java.util.Map;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** The tire-specific effect of a V3 approval; it never writes ordinary ExpenseEntry. */
@Service
public class TireApprovalEffect {
    private final JdbcTemplate jdbc;
    private final CurrentUserService users;
    private final AuditWriteService audit;

    public TireApprovalEffect(JdbcTemplate jdbc, CurrentUserService users, AuditWriteService audit) {
        this.jdbc = jdbc;
        this.users = users;
        this.audit = audit;
    }

    public void validateRequest(Long requestId, AuthenticatedUser user) {
        if (requestId == null) throw invalid("请选择换胎申请");
        Map<String, Object> request = request(requestId);
        if (request == null || request.get("approval_instance_id") != null)
            throw invalid("换胎申请不存在或已经提交审批");
        if (!users.isDriverUser(user) || users.requireDriver(user).outsourced()
                || ((Number) request.get("applicant_driver_id")).longValue()
                != users.requireDriver(user).driverId())
            throw new AccessDeniedException("只能发起本人的换胎申请");
        List<Map<String, Object>> requests = batchRequests(String.valueOf(request.get("batch_key")));
        if (requests.isEmpty()) throw invalid("换胎申请没有轮胎明细");
        for (Map<String, Object> item : requests) {
        Long available = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tire t
                WHERE t.id=? AND t.status='AVAILABLE' AND t.deleted_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM tire_request r WHERE r.tire_id=t.id
                    AND r.id<>? AND r.status IN ('PENDING_APPROVAL','APPROVED'))
                """, Long.class, item.get("tire_id"), item.get("id"));
        if (available == null || available != 1) throw invalid("胎号 " + item.get("confirmed_tire_no") + " 不存在或已被申领");
        }
    }

    public void onCreated(long requestId, long instanceId) {
        Map<String, Object> anchor = request(requestId);
        if (anchor == null) throw invalid("换胎申请不存在");
        List<Map<String, Object>> requests = batchRequests(String.valueOf(anchor.get("batch_key")));
        int updated = jdbc.update("""
                UPDATE tire_request SET approval_instance_id=?,status='PENDING_APPROVAL'
                WHERE batch_key=? AND approval_instance_id IS NULL
                """, instanceId, anchor.get("batch_key"));
        if (updated != requests.size()) throw invalid("换胎申请已经提交审批");
        Map<String, Object> submission = jdbc.queryForList("""
                SELECT id FROM approval_submission_version WHERE instance_id=? ORDER BY version_no DESC LIMIT 1
                """, instanceId).stream().findFirst().orElseThrow();
        for (Map<String, Object> item : requests)
            jdbc.update("""
                    INSERT INTO approval_submission_attachment(submission_version_id,attachment_id) VALUES(?,?)
                    """, submission.get("id"), item.get("photo_attachment_id"));
    }

    public void onReturnedToApplicant(long instanceId) {
        jdbc.update("""
                UPDATE tire_request SET status='RETURNED_TO_APPLICANT'
                WHERE approval_instance_id=? AND status='PENDING_APPROVAL'
                """, instanceId);
    }

    public void onWithdrawn(long instanceId) {
        jdbc.update("""
                UPDATE tire_request SET status='WITHDRAWN'
                WHERE approval_instance_id=? AND status IN ('PENDING_APPROVAL','RETURNED_TO_APPLICANT')
                """, instanceId);
    }

    public void onApproved(long instanceId, long operatorId) {
        List<Map<String, Object>> requests = jdbc.queryForList("""
                SELECT * FROM tire_request WHERE approval_instance_id=?
                  AND batch_key=(SELECT b.batch_key FROM tire_request b
                    WHERE b.approval_instance_id=? ORDER BY b.id DESC LIMIT 1)
                ORDER BY id
                """, instanceId, instanceId);
        if (requests.isEmpty() || requests.stream().anyMatch(item -> !"PENDING_APPROVAL".equals(item.get("status"))))
            throw invalid("换胎申请状态已变化");
        for (Map<String, Object> request : requests) {
        String tireNo = (String) request.get("confirmed_tire_no");
        Map<String, Object> tire = jdbc.queryForList("""
                SELECT id,status FROM tire WHERE tire_no=? AND deleted_at IS NULL FOR UPDATE
                """, tireNo).stream().findFirst().orElse(null);
        if (tire == null || !"AVAILABLE".equals(tire.get("status")))
            throw invalid("轮胎已被申领，不能重复生效");
        long tireId = ((Number) tire.get("id")).longValue();
        if (jdbc.update("UPDATE tire SET status='CLAIMED',version=version+1,updated_by=? WHERE id=? AND status='AVAILABLE'",
                operatorId, tireId) != 1) throw invalid("轮胎已被申领，不能重复生效");
        jdbc.update("""
                INSERT INTO tire_history(tire_id,field_name,before_value,after_value,reason,operator_id)
                VALUES(?,'status','AVAILABLE','CLAIMED',?,?)
                """, tireId, "换胎审批通过：" + instanceId, operatorId);
        jdbc.update("UPDATE tire_request SET status='APPROVED' WHERE id=?", request.get("id"));
        audit.write("TIRE", "TIRE", tireId, "APPROVAL_CLAIM",
                Map.of("status", "AVAILABLE"), Map.of("status", "CLAIMED"),
                operatorId, "换胎审批通过");
        }
    }

    private List<Map<String, Object>> batchRequests(String batchKey) {
        return jdbc.queryForList("SELECT * FROM tire_request WHERE batch_key=? ORDER BY id FOR UPDATE", batchKey);
    }

    private Map<String, Object> request(long id) {
        return jdbc.queryForList("SELECT * FROM tire_request WHERE id=? FOR UPDATE", id)
                .stream().findFirst().orElse(null);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.SYS_002.name(), message);
    }
}
