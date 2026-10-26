package com.erp.portal.administration;

import com.erp.portal.administration.UserDtos.*;
import com.erp.portal.auth.api.RoleAssignmentRequest;
import com.erp.portal.auth.service.RbacService;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserManagementController {
    private final UserManagementService userService;
    private final RbacService rbacService;

    public UserManagementController(UserManagementService userService, RbacService rbacService) {
        this.userService = userService;
        this.rbacService = rbacService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER:CREATE')")
    public ResponseEntity<UserDto> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal,
            HttpServletRequest httpRequest) {
        UserDto created = userService.createUser(request, principal, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER:READ')")
    public ResponseEntity<UserPageResponse> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.listUsers(search, status, role, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER:READ')")
    public ResponseEntity<UserDto> getUser(@PathVariable long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER:UPDATE')")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userService.updateUser(id, request, principal, httpRequest));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER:MANAGE')")
    public ResponseEntity<UserDto> updateUserStatus(
            @PathVariable long id,
            @Valid @RequestBody UserStatusRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userService.updateUserStatus(id, request.status(), principal, httpRequest));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('RBAC:USER_ROLE_UPDATE')")
    public ResponseEntity<Void> updateUserRoles(
            @PathVariable long id,
            @Valid @RequestBody RoleAssignmentRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal,
            HttpServletRequest httpRequest) {
        rbacService.replaceUserRoles(id, request.roleCodes(), principal, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
