package com.erp.portal.operations;

import com.erp.portal.common.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice(basePackages = "com.erp.portal.operations")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OperationsExceptionHandler {

    @ExceptionHandler(OperationsException.class)
    public ResponseEntity<ApiErrorResponse> handleOperationsException(OperationsException e, HttpServletRequest request) {
        return body(e.status().value(), e.code(), e.getMessage(), request, List.of());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException e, HttpServletRequest request) {
        return body(403, "FORBIDDEN", "You do not have permission to access this operations resource", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(DataIntegrityViolationException e, HttpServletRequest request) {
        return body(409, "CONFLICT", "Operations entity conflicts with existing relationship or unique constraint", request, List.of());
    }

    private static ResponseEntity<ApiErrorResponse> body(int status, String code, String message,
                                                         HttpServletRequest request, List<ApiErrorResponse.FieldErrorDetail> fields) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status,
                code,
                message,
                request.getRequestURI(),
                fields,
                MDC.get("traceId") == null ? "unavailable" : MDC.get("traceId")
        ));
    }
}
