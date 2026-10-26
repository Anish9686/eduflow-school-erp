package com.erp.portal.security;

import com.erp.portal.auth.persistence.AuthStore;
import com.erp.portal.common.api.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BearerAuthenticationFilter extends OncePerRequestFilter {
    private final AuthStore authStore;
    private final ApiErrorWriter errorWriter;

    public BearerAuthenticationFilter(AuthStore authStore, ApiErrorWriter errorWriter) {
        this.authStore = authStore;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : "";
        if (!token.matches("[A-Za-z0-9_-]{43}")) {
            reject(request, response);
            return;
        }

        SecurityPrincipal principal = authStore.findActiveSession(OpaqueToken.digest(token)).orElse(null);
        if (principal == null) {
            reject(request, response);
            return;
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        principal.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        principal.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED", "Authentication is required");
    }
}
