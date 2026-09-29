package com.dfwl.fleet.route.service;

import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.route.dto.request.TaskAmountAdjustmentRequest;
import com.dfwl.fleet.route.dto.response.TaskAmountHistoryResponse;
import com.dfwl.fleet.system.service.AuditWriteService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskAmountService {
    private final JdbcTemplate jdbc;
    private final AuditWriteService audit;

    public TaskAmountService(JdbcTemplate jdbc, AuditWriteService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public List<TaskAmountHistoryResponse> history(long taskId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM transport_task WHERE id = ? AND deleted_at IS NULL",
                Integer.class, taskId) == 0) throw new BusinessException(ErrorCode.ROUTE_001);
        return jdbc.query("""
                SELECT id, amount_kind, before_amount, after_amount, reason, operator_id, operated_at
                FROM task_amount_history WHERE task_id = ? ORDER BY id
                """, (rs, row) -> new TaskAmountHistoryResponse(rs.getLong("id"), rs.getString("amount_kind"),
                rs.getBigDecimal("before_amount"), rs.getBigDecimal("after_amount"), rs.getString("reason"),
                rs.getLong("operator_id"), rs.getTimestamp("operated_at").toLocalDateTime()), taskId);
    }

    @Transactional
    public void adjust(long taskId, TaskAmountAdjustmentRequest request, long operatorId) {
        if (request == null || request.kind() == null) throw new BusinessException(ErrorCode.SYS_002);
        String column = switch (request.kind()) {
            case "UNIT_PRICE" -> "actual_unit_price";
            case "INFORMATION_FEE" -> "actual_information_fee";
            default -> throw new BusinessException(ErrorCode.SYS_002);
        };
        if (request.expectedVersion() == null || request.newAmount() == null
                || request.newAmount().signum() < 0 || request.newAmount().scale() > 2
                || request.newAmount().precision() - request.newAmount().scale() > 16
                || request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_002);
        }
        List<AmountFact> facts = jdbc.query("SELECT " + column + " AS amount, version, status FROM transport_task WHERE id = ? AND deleted_at IS NULL FOR UPDATE",
                (rs, row) -> new AmountFact(rs.getBigDecimal("amount"), rs.getInt("version"), rs.getString("status")), taskId);
        if (facts.isEmpty()) throw new BusinessException(ErrorCode.ROUTE_001);
        AmountFact fact = facts.getFirst();
        if (fact.version != request.expectedVersion() || "CANCELLED".equals(fact.status)) {
            throw new BusinessException(ErrorCode.ROUTE_002);
        }
        if (fact.amount.compareTo(request.newAmount()) == 0) return;
        int changed = jdbc.update("UPDATE transport_task SET " + column + " = ?, version = version + 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND version = ?",
                request.newAmount(), operatorId, taskId, fact.version);
        if (changed != 1) throw new BusinessException(ErrorCode.ROUTE_002);
        jdbc.update("""
                INSERT INTO task_amount_history (task_id, amount_kind, before_amount, after_amount, reason, operator_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, taskId, request.kind(), fact.amount, request.newAmount(), request.reason().trim(), operatorId);
        audit.write("TASK", "TRANSPORT_TASK", taskId, "TASK_AMOUNT_ADJUST",
                Map.of("kind", request.kind(), "amount", fact.amount),
                Map.of("kind", request.kind(), "amount", request.newAmount()), operatorId, request.reason().trim());
    }

    private record AmountFact(BigDecimal amount, int version, String status) {}
}
