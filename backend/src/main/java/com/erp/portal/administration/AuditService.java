package com.erp.portal.administration;

import com.erp.portal.common.api.RequestTraceFilter;
import com.erp.portal.security.SecurityPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void record(String action, String entityType, String entityId, SecurityPrincipal actor,
                       String actorUsername, Object oldValue, Object newValue, HttpServletRequest request) {
        jdbc.update("INSERT INTO audit_logs (actor_user_id, actor_username, action, entity_type, entity_id, " +
                        "old_value, new_value, ip_address, trace_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                actor == null ? null : actor.userId(), actor == null ? actorUsername : actor.username(),
                action, entityType, entityId, json(oldValue), json(newValue),
                request == null ? null : request.getRemoteAddr(), MDC.get(RequestTraceFilter.MDC_KEY));
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode audit data", exception);
        }
    }
}
