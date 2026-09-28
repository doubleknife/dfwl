package com.dfwl.fleet.route;

import org.springframework.jdbc.core.JdbcTemplate;

/** Existing route tests supply an already uploaded ticket through the formal attachment table. */
public final class RouteTicketFixture {
    private RouteTicketFixture() {}

    public static long ticket(JdbcTemplate jdbc, long routeId, long uploadedBy) {
        Long next = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM file_attachment", Long.class);
        long id = next == null ? 1 : next;
        jdbc.update("""
                INSERT INTO file_attachment
                    (id, owner_type, owner_id, purpose, storage_key, original_filename,
                     content_type, file_size, uploaded_by)
                VALUES (?, 'ROUTE', ?, 'ROUTE_UNLOAD_WEIGHT_TICKET', ?, 'ticket.png',
                        'image/png', 12, ?)
                """, id, routeId, "test/ticket-" + id + ".png", uploadedBy);
        return id;
    }
}
