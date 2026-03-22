package com.paymentplatform.commonlib.security;

/**
 * Internal header names used by the API gateway to propagate
 * authenticated user identity to downstream services.
 *
 * These headers are set by {@code JwtHeaderRelayFilter} in the gateway
 * after JWT validation, and consumed by {@code GatewayAuthFilter}
 * in each downstream service to build a SecurityContext.
 *
 * SECURITY: These headers must NEVER be trusted from external sources.
 * The gateway strips any incoming values before setting them from the JWT.
 * Network isolation (K8s ClusterIP / NetworkPolicy) ensures downstream
 * services are only reachable through the gateway.
 */
public final class GatewayAuthConstants {

    private GatewayAuthConstants() {
        // utility class
    }

    /** User ID extracted from JWT "sub" claim. */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** Comma-separated roles from Keycloak realm_access.roles. */
    public static final String HEADER_USER_ROLES = "X-User-Roles";
}
