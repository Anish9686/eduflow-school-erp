package com.erp.portal.attendance;

import org.springframework.http.HttpStatus;

public final class AttendanceException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String field;
    private AttendanceException(HttpStatus status, String code, String message, String field) {
        super(message); this.status=status; this.code=code; this.field=field;
    }
    public HttpStatus status(){return status;}
    public String code(){return code;}
    public String field(){return field;}
    public static AttendanceException notFound(String item){return new AttendanceException(HttpStatus.NOT_FOUND,"NOT_FOUND",item+" was not found",null);}
    public static AttendanceException forbidden(){return new AttendanceException(HttpStatus.FORBIDDEN,"FORBIDDEN","You do not have permission for this attendance scope",null);}
    public static AttendanceException conflict(String message){return new AttendanceException(HttpStatus.CONFLICT,"CONFLICT",message,null);}
    public static AttendanceException invalid(String field,String message){return new AttendanceException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",message,field);}
}
