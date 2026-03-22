package com.paymentplatform.commonlib.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Servlet filter for downstream services (order, payment, inventory, etc.)
 * that builds a Spring Security authentication from gateway-provided headers.
 *
 * <p>In the gateway-trust model, the API gateway validates the JWT and
 * forwards the user identity via {@code X-User-Id} and {@code X-User-Roles}
 * headers. This filter reads those headers and populates the
 * {@link SecurityContextHolder} so that downstream controllers and services
 * can use {@code @AuthenticationPrincipal} or {@code SecurityContextHolder}
 * to access the authenticated user.</p>
 *
 * <p>This class is NOT a Spring component — it is manually instantiated
 * and registered in each service's {@code SecurityConfig} to avoid
 * classpath issues with the WebFlux-based API gateway (which also
 * depends on common-lib but does not have the servlet API).</p>
 *
 * @see GatewayAuthConstants
 */
public class GatewayAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader(GatewayAuthConstants.HEADER_USER_ID);
        String userRoles = request.getHeader(GatewayAuthConstants.HEADER_USER_ROLES);

        if (userId != null && !userId.isBlank()) {
            List<GrantedAuthority> authorities = parseRoles(userRoles);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear to prevent context leaking across requests
            // (especially with virtual threads / thread reuse)
            SecurityContextHolder.clearContext();
        }
    }

    private List<GrantedAuthority> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
