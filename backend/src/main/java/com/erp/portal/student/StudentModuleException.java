package com.erp.portal.student;

public class StudentModuleException extends RuntimeException {
    private final int status;
    private final String code;
    private final String field;

    private StudentModuleException(int status, String code, String field, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public int status() { return status; }
    public String code() { return code; }
    public String field() { return field; }

    public static StudentModuleException notFound() {
        return new StudentModuleException(404, "STUDENT_NOT_FOUND", null, "Student record was not found");
    }
    public static StudentModuleException admissionNotFound() {
        return new StudentModuleException(404, "ADMISSION_NOT_FOUND", null, "Admission record was not found");
    }
    public static StudentModuleException documentNotFound() {
        return new StudentModuleException(404, "DOCUMENT_NOT_FOUND", null, "Document was not found");
    }
    public static StudentModuleException conflict(String field, String message) {
        return new StudentModuleException(409, "CONFLICT", field, message);
    }
    public static StudentModuleException invalid(String field, String message) {
        return new StudentModuleException(400, "VALIDATION_ERROR", field, message);
    }
}
