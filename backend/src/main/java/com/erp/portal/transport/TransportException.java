package com.erp.portal.transport;

import org.springframework.http.HttpStatus;

public class TransportException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String field;

    public TransportException(HttpStatus status, String code, String message) {
        this(status, code, null, message);
    }

    public TransportException(HttpStatus status, String code, String field, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }

    public static TransportException notFound(String message) {
        return new TransportException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static TransportException conflict(String message) {
        return new TransportException(HttpStatus.CONFLICT, "CONFLICT", null, message);
    }

    public static TransportException conflict(String field, String message) {
        return new TransportException(HttpStatus.CONFLICT, "CONFLICT", field, message);
    }

    public static TransportException badRequest(String field, String message) {
        return new TransportException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", field, message);
    }

    public static TransportException forbidden(String message) {
        return new TransportException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
