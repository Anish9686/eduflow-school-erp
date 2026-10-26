package com.erp.portal.student.api;

import com.erp.portal.common.api.ApiErrorResponse;
import com.erp.portal.student.StudentModuleException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice(basePackages = "com.erp.portal.student")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StudentExceptionHandler {
    @ExceptionHandler(StudentModuleException.class)
    public ResponseEntity<ApiErrorResponse> handleStudentError(StudentModuleException exception,
                                                               HttpServletRequest request) {
        var errors = exception.field() == null ? List.<ApiErrorResponse.FieldErrorDetail>of()
                : List.of(new ApiErrorResponse.FieldErrorDetail(exception.field(), exception.getMessage()));
        return response(exception.status(), exception.code(), exception.getMessage(), request, errors);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintConflict(DataIntegrityViolationException exception,
                                                                     HttpServletRequest request) {
        String cause = exception.getMostSpecificCause().getMessage();
        String constraint = cause == null ? "" : cause.toLowerCase(java.util.Locale.ROOT);
        if (constraint.contains("uq_admissions_application_no")) {
            return response(409, "CONFLICT", "Application number already exists; use a unique application number",
                    request, List.of(new ApiErrorResponse.FieldErrorDetail("applicationNo", "must be unique")));
        }
        if (constraint.contains("uq_students_admission_no")) {
            return response(409, "CONFLICT", "Student admission number is already assigned; use a unique number",
                    request, List.of(new ApiErrorResponse.FieldErrorDetail("admissionNo", "must be unique")));
        }
        throw exception;
    }

    private static ResponseEntity<ApiErrorResponse> response(int status, String code, String message,
            HttpServletRequest request, List<ApiErrorResponse.FieldErrorDetail> errors) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(Instant.now(), status, code, message,
                request.getRequestURI(), errors,
                MDC.get("traceId") == null ? "unavailable" : MDC.get("traceId")));
    }
}
