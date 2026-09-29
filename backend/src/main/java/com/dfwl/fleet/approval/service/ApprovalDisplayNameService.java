package com.dfwl.fleet.approval.service;

import com.dfwl.fleet.approval.dto.response.ApprovalDetailResponse.SubmissionVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only current names for authorized approval details; frozen snapshots remain untouched. */
@Service
public class ApprovalDisplayNameService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ApprovalDisplayNameService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Map<String, Map<String, String>> resolve(List<SubmissionVersion> submissions) {
        Map<String, Map<String, String>> names = new HashMap<>();
        names.put("drivers", new HashMap<>());
        names.put("vehicles", new HashMap<>());
        names.put("routes", new HashMap<>());
        for (SubmissionVersion submission : submissions) {
            try {
                JsonNode snapshot = mapper.readTree(submission.businessSnapshotJson());
                collect(snapshot, names);
                if (snapshot.path("items").isArray()) {
                    snapshot.path("items").forEach(item -> collect(item, names));
                }
            } catch (Exception ignored) {
                // Legacy snapshots can be malformed; the original evidence is still returned.
            }
        }
        return names;
    }

    private void collect(JsonNode item, Map<String, Map<String, String>> names) {
        lookup(item.path("driverId"), names.get("drivers"), "SELECT name FROM driver WHERE id = ?");
        lookup(item.path("vehicleId"), names.get("vehicles"), "SELECT plate_no FROM vehicle WHERE id = ?");
        lookup(item.path("routeId"), names.get("routes"), "SELECT route_no FROM transport_task WHERE id = ?");
    }

    private void lookup(JsonNode id, Map<String, String> labels, String sql) {
        if (!id.canConvertToLong() || id.asLong() <= 0 || labels.containsKey(id.asText())) return;
        List<String> values = jdbc.query(sql, (rs, rowNum) -> rs.getString(1), id.asLong());
        if (!values.isEmpty()) labels.put(id.asText(), values.get(0));
    }
}
