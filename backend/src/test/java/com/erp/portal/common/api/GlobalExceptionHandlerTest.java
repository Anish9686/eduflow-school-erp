package com.erp.portal.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validationFailureUsesCommonErrorContract() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "name", "", false, null, null, "must not be blank"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, binding);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/foundation-check");

        var response = handler.handleInvalidArguments(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/foundation-check");
        assertThat(response.getBody().fieldErrors()).containsExactly(
                new ApiErrorResponse.FieldErrorDetail("name", "must not be blank"));
    }

    @Test
    void unexpectedFailureDoesNotExposeExceptionDetails() {
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/foundation-check");

        var response = handler.handleUnexpected(new IllegalStateException("private detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().message()).doesNotContain("private detail");
    }

    @Test
    void responseStatusExceptionPreservesReasonAndMapsStatus() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/finance/demands");
        var ex = new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST, "billingPeriod: is outside the fee structure's effective dates");

        var response = handler.handleResponseStatus(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("billingPeriod: is outside the fee structure's effective dates");
    }

    @Test
    void responseStatusExceptionConflictMapsToConflictCode() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/finance/demands");
        var ex = new org.springframework.web.server.ResponseStatusException(
                HttpStatus.CONFLICT, "A demand already exists for this student, fee structure and billing period");

        var response = handler.handleResponseStatus(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("A demand already exists for this student, fee structure and billing period");
    }
}
