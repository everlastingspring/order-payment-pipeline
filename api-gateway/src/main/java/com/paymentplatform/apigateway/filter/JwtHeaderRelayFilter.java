package com.paymentplatform.apigateway.filter;

import com.paymentplatform.commonlib.security.GatewayAuthConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Gateway global filter that extracts identity from a validated JWT
 * and propagates it as internal headers to downstream services.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Spring Security validates the JWT (via {@code oauth2ResourceServer})</li>
 *   <li>This filter reads the authenticated {@code JwtAuthenticationToken}</li>
 *   <li>Strips any incoming {@code X-User-Id} / {@code X-User-Roles}
 *       headers to prevent external spoofing</li>
 *   <li>Sets new header values from the JWT claims</li>
 *   <li>Request is forwarded to the downstream service with trusted headers</li>
 * </ol>
 *
 * <p>Only active in the {@code prod} profile. In local dev, the
 * {@code RateLimiterConfig} falls back to {@code X-User-Id} header
 * or client IP for rate limiting.</p>
 *
 * @see GatewayAuthConstants
 */
@Component
@Profile("prod")
public class JwtHeaderRelayFilter implements GlobalFilter, Ordered {

    /**
     * Run after Spring Security filter (order 0) but before routing filters.
     */
    private static final int FILTER_ORDER = 1;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Always strip internal auth headers to prevent external spoofing
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(GatewayAuthConstants.HEADER_USER_ID);
                    headers.remove(GatewayAuthConstants.HEADER_USER_ROLES);
                });

        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> {
                    String userId = auth.getToken().getSubject();
                    List<String> roles = extractKeycloakRoles(auth.getToken().getClaims());

                    requestBuilder
                            .header(GatewayAuthConstants.HEADER_USER_ID, userId)
                            .header(GatewayAuthConstants.HEADER_USER_ROLES, String.join(",", roles));

                    return exchange.mutate()
                            .request(requestBuilder.build())
                            .build();
                })
                .defaultIfEmpty(exchange.mutate().request(requestBuilder.build()).build())
                .flatMap(chain::filter);
    }

    /**
     * Extracts roles from Keycloak's JWT structure.
     * Keycloak stores realm roles at: {@code realm_access.roles} (List of String).
     */
    @SuppressWarnings("unchecked")
    private List<String> extractKeycloakRoles(Map<String, Object> claims) {
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmAccessMap) {
            Object roles = realmAccessMap.get("roles");
            if (roles instanceof List<?> roleList) {
                return roleList.stream()
                        .map(Object::toString)
                        .toList();
            }
        }
        return Collections.emptyList();
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
}
