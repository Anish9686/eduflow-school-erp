package com.erp.portal.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {
    public static final String MDC_KEY = "traceId";
    public static final String RESPONSE_HEADER = "X-Trace-Id";
    public static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incomingId = request.getHeader(CORRELATION_HEADER);
        if (incomingId == null || incomingId.isBlank()) {
            incomingId = request.getHeader(RESPONSE_HEADER);
        }
        if (incomingId == null || incomingId.isBlank()) {
            incomingId = request.getHeader("X-Request-ID");
        }

        String traceId;
        if (incomingId != null && !incomingId.isBlank() && incomingId.length() <= 64) {
            String sanitized = incomingId.replaceAll("[^a-zA-Z0-9_-]", "");
            traceId = sanitized.isEmpty() ? UUID.randomUUID().toString() : sanitized;
        } else {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, traceId);
        response.setHeader(RESPONSE_HEADER, traceId);
        response.setHeader(CORRELATION_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
