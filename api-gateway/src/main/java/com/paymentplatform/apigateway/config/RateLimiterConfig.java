package com.paymentplatform.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

/**
 * Defines how the Redis rate limiter identifies callers.
 *
 * Strategy: per-user rate limiting (all environments).
 *
 * Resolution order:
 *   1. Authenticated user ID from SecurityContext (JWT "sub" claim)
 *   2. X-User-Id header (for testing without JWT / local dev)
 *   3. Client IP as last resort
 *
 * This ensures rate limits follow the user, not the IP —
 * critical when multiple users share an IP (NAT, corporate proxy).
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                // Extract principal name from the ReactorContext — this works both in
                // production (SecurityContextServerWebExchangeWebFilter populates it) and
                // in unit tests (contextWrite(ReactiveSecurityContextHolder.withSecurityContext(...))).
                // exchange.getPrincipal() is intentionally avoided: MockServerWebExchange does not
                // populate principal from the ReactorContext, making it untestable without a filter chain.
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated()
                        && !"anonymousUser".equals(auth.getName()))
                .map(Authentication::getName)
                .switchIfEmpty(Mono.defer(() -> {
                    // Fallback 1: explicit header (useful for local dev / testing)
                    String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
                    if (userId != null && !userId.isBlank()) {
                        return Mono.just(userId);
                    }

                    // Fallback 2: client IP
                    String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
                    if (forwarded != null && !forwarded.isBlank()) {
                        return Mono.just(forwarded.split(",")[0].trim());
                    }

                    var remoteAddress = exchange.getRequest().getRemoteAddress();
                    if (remoteAddress != null) {
                        return Mono.just(remoteAddress.getAddress().getHostAddress());
                    }

                    return Mono.just("anonymous");
                }));
    }
}
