package com.paymentplatform.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

/**
 * Configures the {@link KeyResolver} used by Spring Cloud Gateway's Redis rate limiter.
 *
 * <p><strong>Strategy:</strong> per-user rate limiting across all environments.</p>
 *
 * <p><strong>Key resolution order:</strong></p>
 * <ol>
 *   <li>Authenticated user ID from the reactive {@code SecurityContext} (JWT {@code sub} claim) —
 *       primary path in production after JWT validation.</li>
 *   <li>{@code X-User-Id} request header — useful in local dev and integration tests
 *       where Keycloak is not running.</li>
 *   <li>{@code X-Forwarded-For} header (first entry) — for requests behind a load balancer
 *       where no identity is available.</li>
 *   <li>Direct remote socket IP — final fallback for direct connections.</li>
 *   <li>{@code "anonymous"} — ensures the rate limiter always has a key; anonymous
 *       requests share one bucket.</li>
 * </ol>
 *
 * <p>Tying limits to user ID rather than IP is essential in production where many
 * users share the same IP (corporate NAT, mobile carrier NAT).</p>
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Creates a {@link KeyResolver} that resolves the rate-limit bucket key for each request.
     * Reads from the reactive {@code SecurityContext} rather than {@code exchange.getPrincipal()}
     * to ensure testability — {@code MockServerWebExchange} does not populate the principal
     * from the Reactor context.
     *
     * @return reactive key resolver bean
     */
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
