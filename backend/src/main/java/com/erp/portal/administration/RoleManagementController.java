package com.erp.portal.administration;

import com.erp.portal.administration.RoleDtos.*;
import com.erp.portal.auth.api.RolePermissionsRequest;
import com.erp.portal.auth.service.RbacService;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin")
public class RoleManagementController {
    private final RoleManagementService roleService;
    private final RbacService rbacService;

    public RoleManagementController(RoleManagementService roleService, RbacService rbacService) {
        this.roleService = roleService;
        this.rbacService = rbacService;
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('RBAC:ROLE_CREATE')")
    public ResponseEntity<RoleDto> createRole(
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal,
            HttpServletRequest httpRequest) {
        RoleDto created = roleService.createRole(request, principal, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('RBAC:ROLE_READ')")
    public ResponseEntity<List<RoleDto>> listRoles() {
        return ResponseEntity.ok(roleService.listRoles());
    }

    @GetMapping("/roles/{code}")
    @PreAuthorize("hasAuthority('RBAC:ROLE_READ')")
    public ResponseEntity<RoleDto> getRole(@PathVariable String code) {
        return ResponseEntity.ok(roleService.getRole(code));
    }

    @PutMapping("/roles/{code}")
    @PreAuthorize("hasAuthority('RBAC:ROLE_UPDATE')")
    public ResponseEntity<RoleDto> updateRole(
            @PathVariable String code,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(roleService.updateRole(code, request, principal, httpRequest));
    }

    @PutMapping("/roles/{code}/permissions")
    @PreAuthorize("hasAuthority('RBAC:ROLE_PERMISSION_UPDATE')")
    public ResponseEntity<Void> updateRolePermissions(
            @PathVariable String code,
            @Valid @RequestBody RolePermissionsRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal,
            HttpServletRequest httpRequest) {
        rbacService.replaceRolePermissions(code, request.permissionCodes(), principal, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('RBAC:ROLE_READ')")
    public ResponseEntity<List<PermissionDto>> listPermissions() {
        return ResponseEntity.ok(roleService.listPermissions());
    }
}
