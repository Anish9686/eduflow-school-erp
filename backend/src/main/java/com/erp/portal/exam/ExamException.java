package com.erp.portal.exam;

public class ExamException extends RuntimeException {
    private final int status;
    private final String code;
    private final String field;

    private ExamException(int status, String code, String field, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public int status() { return status; }
    public String code() { return code; }
    public String field() { return field; }

    public static ExamException notFound(String item) {
        return new ExamException(404, "EXAM_NOT_FOUND", null, item + " was not found");
    }

    public static ExamException badRequest(String message) {
        return new ExamException(400, "BAD_REQUEST", null, message);
    }

    public static ExamException badRequest(String field, String message) {
        return new ExamException(400, "BAD_REQUEST", field, message);
    }

    public static ExamException invalid(String field, String message) {
        return new ExamException(400, "VALIDATION_ERROR", field, message);
    }

    public static ExamException conflict(String field, String message) {
        return new ExamException(409, "CONFLICT", field, message);
    }

    public static ExamException forbidden() {
        return new ExamException(403, "FORBIDDEN", null, "You are not authorized for this examination scope");
    }

    public static ExamException forbidden(String message) {
        return new ExamException(403, "FORBIDDEN", null, message);
    }
}
