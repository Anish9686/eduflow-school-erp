package com.erp.portal.security;

import java.util.List;
import java.util.Set;

/** Authenticated identity and current authorization grants for this request. */
public record SecurityPrincipal(
        long userId,
        String username,
        String fullName,
        List<String> roles,
        Set<String> permissions,
        String sessionHash) {
}
