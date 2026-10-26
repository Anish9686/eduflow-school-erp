package com.erp.portal.auth.api;

import com.erp.portal.auth.service.RbacService;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/security")
public class RbacAdminController {
    private final RbacService rbacService;

    public RbacAdminController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('RBAC:USER_ROLE_UPDATE')")
    public ResponseEntity<Void> replaceUserRoles(@PathVariable("userId") long userId,
                                                  @Valid @RequestBody RoleAssignmentRequest request,
                                                  @AuthenticationPrincipal SecurityPrincipal actor,
                                                  HttpServletRequest servletRequest) {
        rbacService.replaceUserRoles(userId, request.roleCodes(), actor, servletRequest);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/roles/{roleCode}/permissions")
    @PreAuthorize("hasAuthority('RBAC:ROLE_PERMISSION_UPDATE')")
    public ResponseEntity<Void> replaceRolePermissions(@PathVariable("roleCode") String roleCode,
                                                       @Valid @RequestBody RolePermissionsRequest request,
                                                       @AuthenticationPrincipal SecurityPrincipal actor,
                                                       HttpServletRequest servletRequest) {
        rbacService.replaceRolePermissions(roleCode, request.permissionCodes(), actor, servletRequest);
        return ResponseEntity.noContent().build();
    }
}
