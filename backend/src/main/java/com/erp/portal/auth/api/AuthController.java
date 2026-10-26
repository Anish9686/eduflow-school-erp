package com.erp.portal.auth.api;

import com.erp.portal.auth.service.AuthService;
import com.erp.portal.common.api.ApiErrorResponse;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthService.LoginResult result = authService.login(request.username(), request.password(), servletRequest);
        if (result == null) {
            ApiErrorResponse error = new ApiErrorResponse(Instant.now(), 401, "UNAUTHENTICATED",
                    "Invalid username or password", servletRequest.getRequestURI(), List.of(),
                    MDC.get("traceId") == null ? "unavailable" : MDC.get("traceId"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
        return ResponseEntity.ok(new LoginResponse(result.accessToken(), "Bearer", result.expiresAt()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal SecurityPrincipal principal,
                                       HttpServletRequest request) {
        authService.logout(principal, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal SecurityPrincipal principal) {
        return MeResponse.from(principal);
    }

    @PostMapping("/auth/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               @AuthenticationPrincipal SecurityPrincipal principal,
                                               HttpServletRequest servletRequest) {
        authService.changePassword(principal, request.currentPassword(), request.newPassword(), servletRequest);
        return ResponseEntity.noContent().build();
    }
}
