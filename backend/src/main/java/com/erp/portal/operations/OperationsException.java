package com.erp.portal.operations;

import org.springframework.http.HttpStatus;

public class OperationsException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public OperationsException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
