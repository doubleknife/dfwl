package com.dfwl.fleet.system.service;

import com.dfwl.fleet.common.web.RequestIdHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Writes in the caller's transaction. A failed audit write rolls back the protected action. */
@Service
public class AuditWriteService {
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(password|passwd|token|secret|credential|authorization|api.?key|database.?url).*" );
    private static final Pattern INLINE_SECRET = Pattern.compile(
            "(?i)(secret(?:id|key)|password|token|authorization)\\s*[:=]\\s*[^\\s,;]+" );
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditWriteService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void write(String module, String businessType, long businessId, String action,
                      Map<String, ?> before, Map<String, ?> after, long operatorId, String reason) {
        jdbc.update("""
                INSERT INTO audit_log (module, business_type, business_id, operation_type,
                                       before_json, after_json, operator_id, reason, request_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, module, businessType, businessId, action,
                json(before), json(after), operatorId, scrubText(reason), RequestIdHolder.get());
    }

    private String json(Map<String, ?> values) {
        if (values == null) return null;
        try {
            JsonNode tree = mapper.valueToTree(new TreeMap<>(values));
            redact(tree);
            return mapper.writeValueAsString(tree);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("audit snapshot cannot be serialized", ex);
        }
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            java.util.List<String> names = new java.util.ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (SENSITIVE_KEY.matcher(name).matches()) object.put(name, "[REDACTED]");
                else redact(object.get(name));
            }
        } else if (node.isArray()) {
            node.forEach(this::redact);
        }
    }

    private String scrubText(String text) {
        return text == null ? null : INLINE_SECRET.matcher(text).replaceAll("[REDACTED]");
    }
}
