package com.erp.portal.academic;

import com.erp.portal.common.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice(basePackages = "com.erp.portal.academic")
public class AcademicExceptionHandler {
    @ExceptionHandler(AcademicException.class)
    public ResponseEntity<ApiErrorResponse> handleAcademic(AcademicException e,HttpServletRequest request){
        var details=e.field()==null?List.<ApiErrorResponse.FieldErrorDetail>of():List.of(new ApiErrorResponse.FieldErrorDetail(e.field(),e.getMessage()));
        return body(e.status(),e.code(),e.getMessage(),request,details);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(DataIntegrityViolationException e,HttpServletRequest request){
        return body(409,"CONFLICT","The academic record conflicts with an existing relationship or unique constraint",request,List.of());
    }
    private static ResponseEntity<ApiErrorResponse> body(int status,String code,String message,HttpServletRequest request,List<ApiErrorResponse.FieldErrorDetail> fields){
        return ResponseEntity.status(status).body(new ApiErrorResponse(Instant.now(),status,code,message,request.getRequestURI(),fields,MDC.get("traceId")==null?"unavailable":MDC.get("traceId")));
    }
}
