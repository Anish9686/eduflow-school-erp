package com.erp.portal.academic;

public class AcademicException extends RuntimeException {
    private final int status;
    private final String code;
    private final String field;

    private AcademicException(int status, String code, String field, String message) {
        super(message); this.status = status; this.code = code; this.field = field;
    }
    public int status() { return status; }
    public String code() { return code; }
    public String field() { return field; }
    public static AcademicException notFound(String item) { return new AcademicException(404, "ACADEMIC_NOT_FOUND", null, item + " was not found"); }
    public static AcademicException invalid(String field, String message) { return new AcademicException(400, "VALIDATION_ERROR", field, message); }
    public static AcademicException conflict(String field, String message) { return new AcademicException(409, "CONFLICT", field, message); }
    public static AcademicException forbidden() { return new AcademicException(403, "FORBIDDEN", null, "You are not assigned to this academic scope"); }
}
