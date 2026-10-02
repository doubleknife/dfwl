package com.dfwl.fleet.expense.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only resource existence checks used by approval application validation. */
@Repository
public class ExpenseRepository {
    private final JdbcTemplate jdbc;

    public ExpenseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean vehicleExists(long vehicleId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM vehicle WHERE id=? AND deleted_at IS NULL", Integer.class, vehicleId);
        return count != null && count > 0;
    }

    public boolean driverExists(long driverId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM driver WHERE id=? AND status=1 AND deleted_at IS NULL",
                Integer.class, driverId);
        return count != null && count > 0;
    }

    public boolean routeExists(long taskId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transport_task WHERE id=? AND deleted_at IS NULL",
                Integer.class, taskId);
        return count != null && count > 0;
    }
}
