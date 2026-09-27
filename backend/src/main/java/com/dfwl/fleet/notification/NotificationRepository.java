package com.dfwl.fleet.notification;

import com.dfwl.fleet.common.api.PageResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long unreadCount(long userId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM in_app_notification WHERE user_id = ? AND read_at IS NULL", Long.class, userId);
        return count == null ? 0 : count;
    }

    public PageResponse<Notification> list(long userId, int pageNo, int pageSize) {
        int size = Math.min(100, Math.max(1, pageSize));
        int page = Math.max(1, pageNo);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM in_app_notification WHERE user_id = ?", Long.class, userId);
        List<Notification> records = jdbc.query("""
                SELECT id, type, title, summary, business_type, business_id, read_at, created_at
                FROM in_app_notification WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, this::map, userId, size, (page - 1) * size);
        return new PageResponse<>(page, size, count == null ? 0 : count, records);
    }

    public void markRead(long userId, long notificationId) {
        jdbc.update("UPDATE in_app_notification SET read_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND user_id = ? AND read_at IS NULL", notificationId, userId);
    }

    public void markAllRead(long userId) {
        jdbc.update("UPDATE in_app_notification SET read_at = CURRENT_TIMESTAMP(3) WHERE user_id = ? AND read_at IS NULL", userId);
    }

    public Optional<Long> userForDriver(long driverId) {
        return jdbc.query("""
                SELECT u.id FROM driver d JOIN sys_user u ON u.id = d.user_id OR (d.user_id IS NULL AND u.phone = d.phone)
                WHERE d.id = ? AND d.deleted_at IS NULL AND u.status = 1
                ORDER BY u.id LIMIT 1
                """, (rs, n) -> rs.getLong(1), driverId).stream().findFirst();
    }

    public boolean eligibleApprover(long userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_user u
                JOIN sys_role r ON r.id = u.role_id AND r.status = 1
                JOIN sys_role_permission rp ON rp.role_id = r.id
                JOIN sys_permission p ON p.id = rp.permission_id AND p.status = 1
                WHERE u.id = ? AND u.status = 1 AND r.role_code <> 'DRIVER'
                  AND p.permission_code IN ('approval:process', 'approval:return')
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    public void insert(long userId, String eventKey, String type, String title, String summary,
                       String businessType, long businessId) {
        try {
            jdbc.update("""
                    INSERT INTO in_app_notification (user_id, event_key, type, title, summary, business_type, business_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, userId, eventKey, type, title, summary, businessType, businessId);
        } catch (DuplicateKeyException ignored) {
            // Replayed business events must not create another notification.
        }
    }

    private Notification map(ResultSet rs, int rowNum) throws SQLException {
        var read = rs.getTimestamp("read_at");
        return new Notification(rs.getLong("id"), rs.getString("type"), rs.getString("title"),
                rs.getString("summary"), rs.getString("business_type"), rs.getLong("business_id"),
                read == null ? null : read.toLocalDateTime(), rs.getTimestamp("created_at").toLocalDateTime());
    }

    public record Notification(long id, String type, String title, String summary, String businessType,
                               long businessId, LocalDateTime readAt, LocalDateTime createdAt) {}
}
