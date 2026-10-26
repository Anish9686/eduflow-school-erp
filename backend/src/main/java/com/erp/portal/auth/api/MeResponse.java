package com.erp.portal.auth.api;

import com.erp.portal.security.SecurityPrincipal;

import java.util.List;
import java.util.Set;

public record MeResponse(long id, String username, String fullName, List<String> roles,
                         Set<String> permissions) {
    public static MeResponse from(SecurityPrincipal principal) {
        return new MeResponse(principal.userId(), principal.username(), principal.fullName(),
                principal.roles(), principal.permissions());
    }
}
