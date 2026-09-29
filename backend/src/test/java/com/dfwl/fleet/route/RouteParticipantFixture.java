package com.dfwl.fleet.route;

import org.springframework.jdbc.core.JdbcTemplate;

/** Seeds the V2 participant authority for legacy test fixtures that insert transport_task directly. */
public final class RouteParticipantFixture {
    private RouteParticipantFixture() {}

    public static void backfill(JdbcTemplate jdbc) {
        for (var row : jdbc.queryForList("""
                SELECT id, COALESCE(departure_driver_id, assigned_driver_id) AS driver_id,
                       departure_vehicle_id, driver_salary, status
                FROM transport_task WHERE COALESCE(departure_driver_id, assigned_driver_id) IS NOT NULL
                """)) {
            long routeId = ((Number) row.get("id")).longValue();
            if (jdbc.queryForObject("SELECT COUNT(*) FROM transport_participant WHERE task_id = ?", Integer.class, routeId) > 0)
                continue;
            String routeStatus = (String) row.get("status");
            String participantStatus = switch (routeStatus) {
                case "IN_TRANSIT", "TRANSFER_PENDING" -> "IN_TRANSIT";
                case "COMPLETED", "VOIDED" -> "COMPLETED";
                case "CANCELLED" -> "CANCELLED";
                default -> "ASSIGNED";
            };
            jdbc.update("""
                    INSERT INTO transport_participant
                      (task_id, sequence_no, driver_id, driver_type_snapshot, company_vehicle_id,
                       salary_amount, source_type, status, assigned_at, created_by)
                    VALUES (?, 1, ?, 'INTERNAL', ?, ?, 'INITIAL', ?, CURRENT_TIMESTAMP, 1)
                    """, routeId, row.get("driver_id"), row.get("departure_vehicle_id"),
                    row.get("driver_salary"), participantStatus);
            jdbc.update("""
                    UPDATE transport_task SET current_participant_id =
                      (SELECT id FROM transport_participant WHERE task_id = ? AND sequence_no = 1)
                    WHERE id = ?
                    """, routeId, routeId);
        }
    }
}
