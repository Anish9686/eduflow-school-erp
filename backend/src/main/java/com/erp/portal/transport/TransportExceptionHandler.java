package com.erp.portal.transport;

import com.erp.portal.common.api.ApiErrorResponse;
import com.erp.portal.common.api.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@RestControllerAdvice(basePackages = "com.erp.portal.transport")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TransportExceptionHandler {

    @ExceptionHandler(TransportException.class)
    public ResponseEntity<ApiErrorResponse> handleTransportException(
            TransportException exception, HttpServletRequest request) {
        var errors = exception.field() == null ? List.<ApiErrorResponse.FieldErrorDetail>of()
                : List.of(new ApiErrorResponse.FieldErrorDetail(exception.field(), exception.getMessage()));
        return response(exception.status(), exception.code(), exception.getMessage(), request, errors);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        String cause = exception.getMostSpecificCause().getMessage();
        String constraint = cause == null ? "" : cause.toLowerCase(Locale.ROOT);

        if (constraint.contains("uq_transport_buses_reg") || constraint.contains("registration_no")) {
            return response(HttpStatus.CONFLICT, "DUPLICATE_REGISTRATION",
                    "A bus with this registration number already exists", request,
                    List.of(new ApiErrorResponse.FieldErrorDetail("registrationNo", "Registration number must be unique")));
        }
        if (constraint.contains("uq_transport_routes_code") || constraint.contains("route_code")) {
            return response(HttpStatus.CONFLICT, "DUPLICATE_ROUTE_CODE",
                    "A route with this route code already exists", request,
                    List.of(new ApiErrorResponse.FieldErrorDetail("routeCode", "Route code must be unique")));
        }
        if (constraint.contains("uq_route_stop_order")) {
            return response(HttpStatus.CONFLICT, "DUPLICATE_STOP_ORDER",
                    "A stop with this order index already exists for this route", request,
                    List.of(new ApiErrorResponse.FieldErrorDetail("stopOrder", "Stop order must be unique per route")));
        }
        return response(HttpStatus.CONFLICT, "DATA_INTEGRITY_CONFLICT",
                "Operation conflicts with existing transport data", request, List.of());
    }

    private static ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, String code, String message, HttpServletRequest request,
            List<ApiErrorResponse.FieldErrorDetail> errors) {
        String traceId = MDC.get(RequestTraceFilter.MDC_KEY);
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(), status.value(), code, message, request.getRequestURI(), errors,
                traceId == null ? "unavailable" : traceId));
    }
}
