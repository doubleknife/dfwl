package com.dfwl.fleet.approval.repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalTemplateRepository {

    private final JdbcTemplate jdbc;

    public ApprovalTemplateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long create(String code, String category, String approvalType, long operatorId) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO approval_template (template_code, category, approval_type, created_by)
                    VALUES (?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, code);
            statement.setString(2, category);
            statement.setString(3, approvalType);
            statement.setLong(4, operatorId);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public Optional<TemplateRecord> find(long id) {
        return jdbc.query("""
                SELECT t.id, t.template_code, t.category, t.approval_type,
                       (SELECT f.id FROM approval_flow f
                        WHERE f.template_id = t.id AND f.status = 'ACTIVE') AS active_flow_id
                FROM approval_template t WHERE t.id = ?
                """, (rs, rowNum) -> new TemplateRecord(
                rs.getLong("id"), rs.getString("template_code"), rs.getString("category"),
                rs.getString("approval_type"), nullableLong(rs, "active_flow_id")), id).stream().findFirst();
    }

    public Optional<TemplateRecord> findByCode(String code) {
        return jdbc.query("""
                SELECT t.id, t.template_code, t.category, t.approval_type,
                       (SELECT f.id FROM approval_flow f
                        WHERE f.template_id = t.id AND f.status = 'ACTIVE') AS active_flow_id
                FROM approval_template t WHERE t.template_code = ?
                """, (rs, rowNum) -> new TemplateRecord(
                rs.getLong("id"), rs.getString("template_code"), rs.getString("category"),
                rs.getString("approval_type"), nullableLong(rs, "active_flow_id")), code).stream().findFirst();
    }

    public int activeCountForType(String approvalType) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM approval_flow f
                JOIN approval_template t ON t.id = f.template_id
                WHERE f.approval_type = ? AND f.status = 'ACTIVE'
                  AND t.category <> 'TIRE'
                """, Integer.class, approvalType);
        return count == null ? 0 : count;
    }

    public List<AvailableRecord> available(long roleId) {
        return jdbc.query("""
                SELECT t.id AS template_id, t.template_code, t.category, t.approval_type,
                       f.id AS flow_id, f.version_no, f.flow_name, f.evidence_requirement,
                       e.expense_type, e.allow_single, e.allow_excel, e.allow_route, e.allow_daily
                FROM approval_template t
                JOIN approval_flow f ON f.template_id = t.id
                JOIN approval_flow_initiator_role r ON r.flow_id = f.id AND r.role_id = ?
                LEFT JOIN approval_flow_expense_config e ON e.flow_id = f.id
                WHERE t.category IN ('EXPENSE', 'OTHER')
                  AND f.status = 'ACTIVE' AND f.configuration_status = 'READY'
                  AND (t.category <> 'EXPENSE' OR e.flow_id IS NOT NULL)
                ORDER BY t.id
                """, (rs, rowNum) -> new AvailableRecord(
                rs.getLong("template_id"), rs.getString("template_code"), rs.getString("category"),
                rs.getString("approval_type"), rs.getLong("flow_id"), rs.getInt("version_no"),
                rs.getString("flow_name"), rs.getString("evidence_requirement"),
                rs.getString("expense_type"), rs.getInt("allow_single") == 1,
                rs.getInt("allow_excel") == 1, rs.getInt("allow_route") == 1,
                rs.getInt("allow_daily") == 1), roleId);
    }

    public List<TemplateRecord> list() {
        return jdbc.query("""
                SELECT t.id, t.template_code, t.category, t.approval_type,
                       (SELECT f.id FROM approval_flow f
                        WHERE f.template_id = t.id AND f.status = 'ACTIVE') AS active_flow_id
                FROM approval_template t ORDER BY t.id DESC
                """, (rs, rowNum) -> new TemplateRecord(
                rs.getLong("id"), rs.getString("template_code"), rs.getString("category"),
                rs.getString("approval_type"), nullableLong(rs, "active_flow_id")));
    }

    public boolean codeExists(String code) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM approval_template WHERE template_code = ?", Integer.class, code);
        return count != null && count > 0;
    }

    public void lock(long id) {
        List<Long> rows = jdbc.query("SELECT id FROM approval_template WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong(1), id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("approval template not found");
        }
    }

    public List<Long> versionIds(long templateId) {
        return jdbc.query("SELECT id FROM approval_flow WHERE template_id = ? ORDER BY version_no DESC, id DESC",
                (rs, rowNum) -> rs.getLong(1), templateId);
    }

    public Optional<Long> activeFlowId(long templateId) {
        return jdbc.query("SELECT id FROM approval_flow WHERE template_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getLong(1), templateId).stream().findFirst();
    }

    public Optional<VersionSettings> settings(long flowId) {
        return jdbc.query("""
                SELECT evidence_requirement, configuration_status
                FROM approval_flow WHERE id = ?
                """, (rs, rowNum) -> new VersionSettings(rs.getString(1), rs.getString(2)), flowId)
                .stream().findFirst();
    }

    public void updateConfigurationStatus(long flowId, String status) {
        jdbc.update("UPDATE approval_flow SET configuration_status = ? WHERE id = ?", status, flowId);
    }

    public void saveInitiatorRoles(long flowId, List<Long> roleIds) {
        for (Long roleId : roleIds) {
            jdbc.update("INSERT INTO approval_flow_initiator_role (flow_id, role_id) VALUES (?, ?)", flowId, roleId);
        }
    }

    public List<Long> initiatorRoleIds(long flowId) {
        return jdbc.query("SELECT role_id FROM approval_flow_initiator_role WHERE flow_id = ? ORDER BY role_id",
                (rs, rowNum) -> rs.getLong(1), flowId);
    }

    public boolean canInitiate(long flowId, long roleId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM approval_flow_initiator_role
                WHERE flow_id = ? AND role_id = ?
                """, Integer.class, flowId, roleId);
        return count != null && count > 0;
    }

    public boolean activeRoleExists(long roleId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE id = ? AND status = 1", Integer.class, roleId);
        return count != null && count > 0;
    }

    public void saveExpenseConfig(long flowId, ExpenseSettings config) {
        jdbc.update("""
                INSERT INTO approval_flow_expense_config
                    (flow_id, expense_type, allow_single, allow_excel, allow_route, allow_daily)
                VALUES (?, ?, ?, ?, ?, ?)
                """, flowId, config.expenseType(), config.allowSingle() ? 1 : 0,
                config.allowExcel() ? 1 : 0, config.allowRoute() ? 1 : 0, config.allowDaily() ? 1 : 0);
    }

    public Optional<ExpenseSettings> expenseConfig(long flowId) {
        return jdbc.query("""
                SELECT expense_type, allow_single, allow_excel, allow_route, allow_daily
                FROM approval_flow_expense_config WHERE flow_id = ?
                """, (rs, rowNum) -> new ExpenseSettings(rs.getString(1), rs.getInt(2) == 1,
                rs.getInt(3) == 1, rs.getInt(4) == 1, rs.getInt(5) == 1), flowId).stream().findFirst();
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record TemplateRecord(long id, String templateCode, String category, String approvalType, Long activeFlowId) {
    }

    public record VersionSettings(String evidenceRequirement, String configurationStatus) {
    }

    public record ExpenseSettings(String expenseType, boolean allowSingle, boolean allowExcel,
                                  boolean allowRoute, boolean allowDaily) {
    }

    public record AvailableRecord(long templateId, String templateCode, String category, String approvalType,
                                  long flowId, int versionNo, String flowName, String evidenceRequirement,
                                  String expenseType, boolean allowSingle, boolean allowExcel,
                                  boolean allowRoute, boolean allowDaily) {
    }
}
