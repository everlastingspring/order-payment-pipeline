package com.paymentplatform.apigateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimiterConfig Tests")
class RateLimiterConfigTest {

    private RateLimiterConfig config;
    private KeyResolver keyResolver;

    @BeforeEach
    void setUp() {
        config = new RateLimiterConfig();
        keyResolver = config.userKeyResolver();
    }

    @Nested
    @DisplayName("User Key Resolver")
    class UserKeyResolverTests {
        @Test
        @DisplayName("should use authenticated user principal as key when available")
        void resolver_usesAuthenticatedPrincipal() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/orders")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Inject authenticated principal into the reactive SecurityContext.
            // exchange.getPrincipal() reads from ReactorContext, not a local variable —
            // so we must use contextWrite to attach the SecurityContext to the Mono chain.
            // 3-arg constructor sets authenticated=true, matching what Spring Security's
            // JWT processor produces in production. The 2-arg form sets authenticated=false
            // and would be filtered out by the isAuthenticated() check in the impl.
            SecurityContext securityContext = new SecurityContextImpl(
                    new UsernamePasswordAuthenticationToken("user-123", null, Collections.emptyList()));

            String key = keyResolver.resolve(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                            Mono.just(securityContext)))
                    .block();

            assertThat(key).isEqualTo("user-123");
        }

        @Test
        @DisplayName("should fall back to X-User-Id header when no principal")
        void resolver_fallsBackToXUserIdHeader() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/orders")
                    .header("X-User-Id", "header-user-123")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            String key = keyResolver.resolve(exchange).block();
            assertThat(key).isEqualTo("header-user-123");
        }

        @Test
        @DisplayName("should use first IP from X-Forwarded-For when no user header")
        void resolver_usesForwardedIpAddress() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/wallets")
                    .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            String key = keyResolver.resolve(exchange).block();
            assertThat(key).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should ignore blank X-User-Id header and fall back to IP")
        void resolver_ignoresBlankHeader() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/inventory")
                    .header("X-User-Id", "   ")
                    .header("X-Forwarded-For", "203.0.113.50")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            String key = keyResolver.resolve(exchange).block();
            // Blank X-User-Id must be skipped (isBlank() check in impl);
            // should fall through to first IP in X-Forwarded-For
            assertThat(key).isEqualTo("203.0.113.50");
        }

        @Test
        @DisplayName("should default to 'anonymous' when no key found")
        void resolver_defaultsToAnonymous() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/public")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            String key = keyResolver.resolve(exchange).block();
            // No principal, no X-User-Id, no X-Forwarded-For, no remote address
            // → implementation returns "anonymous" as the final fallback
            assertThat(key).isEqualTo("anonymous");
        }

        @Test
        @DisplayName("should prioritize authenticated user over header")
        void resolver_prioritizesAuthenticationOverHeader() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/orders")
                    .header("X-User-Id", "header-user-999")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Inject a different principal (auth-user-123) alongside the X-User-Id header.
            // Implementation resolves principal FIRST via exchange.getPrincipal() —
            // so auth user must win over the header value.
            SecurityContext securityContext = new SecurityContextImpl(
                    new UsernamePasswordAuthenticationToken("auth-user-123", null, Collections.emptyList()));

            String key = keyResolver.resolve(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                            Mono.just(securityContext)))
                    .block();

            assertThat(key).isEqualTo("auth-user-123");          // auth wins
            assertThat(key).isNotEqualTo("header-user-999");     // header loses
        }

        @Test
        @DisplayName("should trim whitespace from IP addresses")
        void resolver_trimsWhitespaceFromIp() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/data")
                    .header("X-Forwarded-For", "  192.168.1.50  ,  10.0.0.1  ")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            String key = keyResolver.resolve(exchange).block();
            assertThat(key).isEqualTo("192.168.1.50");
        }
    }

    @Nested
    @DisplayName("Rate Limiter Configuration")
    class ConfigurationTests {
        @Test
        @DisplayName("should create KeyResolver bean")
        void config_createsKeyResolverBean() {
            assertThat(keyResolver).isNotNull();
        }

        @Test
        @DisplayName("should ensure per-user rate limiting (not per-IP)")
        void config_implementsPerUserRateLimiting() {
            // Strategy: use authenticated user or X-User-Id header, not just IP
            // This prevents one user on shared IP from exhausting quota
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/orders")
                    .header("X-User-Id", "user-from-shared-ip")
                    .header("X-Forwarded-For", "203.0.113.1")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Should use user ID, not IP
            String key = keyResolver.resolve(exchange).block();
            assertThat(key).isEqualTo("user-from-shared-ip");
        }
    }
}
