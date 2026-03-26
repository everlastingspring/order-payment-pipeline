package com.paymentplatform.apigateway.filter;

import com.paymentplatform.commonlib.security.GatewayAuthConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("prod")
@DisplayName("JwtHeaderRelayFilter Tests")
class JwtHeaderRelayFilterTest {

    private JwtHeaderRelayFilter filter;
    private CapturingGatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtHeaderRelayFilter();
        chain = new CapturingGatewayFilterChain();
    }

    @Nested
    @DisplayName("JWT Header Relay")
    class JwtHeaderRelayTests {
        @Test
        @DisplayName("should have correct filter order")
        void filter_hasCorrectOrder() {
            assertThat(filter.getOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("should be active in prod profile")
        void filter_isActivatedInProdProfile() {
            assertThat(JwtHeaderRelayFilter.class.getAnnotation(org.springframework.context.annotation.Profile.class))
                    .isNotNull();
        }

        @Test
        @DisplayName("should extract Keycloak roles from realm_access claim structure")
        void filter_handlesKeycloakRoles() {
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                    createTestJwt("user-001", List.of("admin", "user"))
            );
            ServerWebExchange exchange = withPrincipal(
                    MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders").build()),
                    authentication
            );

            filter.filter(exchange, chain).block();

            assertThat(chain.lastExchange().getRequest().getHeaders().getFirst(GatewayAuthConstants.HEADER_USER_ID))
                    .isEqualTo("user-001");
            assertThat(chain.lastExchange().getRequest().getHeaders().getFirst(GatewayAuthConstants.HEADER_USER_ROLES))
                    .isEqualTo("admin,user");
        }

        @Test
        @DisplayName("should prevent header spoofing by stripping incoming auth headers")
        void filter_preventsHeaderSpoofing() {
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                    createTestJwt("real-user", List.of("user"))
            );
            ServerWebExchange exchange = withPrincipal(
                    MockServerWebExchange.from(
                            MockServerHttpRequest.get("/api/orders")
                                    .header(GatewayAuthConstants.HEADER_USER_ID, "attacker-user-id")
                                    .header(GatewayAuthConstants.HEADER_USER_ROLES, "admin,superuser")
                                    .build()
                    ),
                    authentication
            );

            filter.filter(exchange, chain).block();

            assertThat(chain.lastExchange().getRequest().getHeaders().getFirst(GatewayAuthConstants.HEADER_USER_ID))
                    .isEqualTo("real-user");
            assertThat(chain.lastExchange().getRequest().getHeaders().getFirst(GatewayAuthConstants.HEADER_USER_ROLES))
                    .isEqualTo("user");
        }

        @Test
        @DisplayName("should handle missing realm_access claim gracefully")
        void filter_handlesNoRolesClaim() {
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                    createJwtWithClaims("user-456", Map.of())
            );
            ServerWebExchange exchange = withPrincipal(
                    MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders").build()),
                    authentication
            );

            filter.filter(exchange, chain).block();

            assertThat(chain.lastExchange().getRequest().getHeaders().getFirst(GatewayAuthConstants.HEADER_USER_ID))
                    .isEqualTo("user-456");
            assertThat(chain.lastExchange().getRequest().getHeaders().getFirst(GatewayAuthConstants.HEADER_USER_ROLES))
                    .isEqualTo("");
        }

        @Test
        @DisplayName("should strip spoofed headers when request is unauthenticated")
        void filter_stripsHeadersWithoutJwtPrincipal() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/orders")
                            .header(GatewayAuthConstants.HEADER_USER_ID, "spoofed-user")
                            .header(GatewayAuthConstants.HEADER_USER_ROLES, "admin")
                            .build()
            );

            filter.filter(exchange, chain).block();

            assertThat(chain.lastExchange().getRequest().getHeaders().containsKey(GatewayAuthConstants.HEADER_USER_ID))
                    .isFalse();
            assertThat(chain.lastExchange().getRequest().getHeaders().containsKey(GatewayAuthConstants.HEADER_USER_ROLES))
                    .isFalse();
        }
    }

    private Jwt createTestJwt(String subject, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        Map<String, Object> realmAccess = new HashMap<>();
        realmAccess.put("roles", roles);
        claims.put("realm_access", realmAccess);
        return createJwtWithClaims(subject, claims);
    }

    private Jwt createJwtWithClaims(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("mock-jwt-token")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claims(existingClaims -> existingClaims.putAll(claims))
                .header("alg", "RS256")
                .build();
    }

    private ServerWebExchange withPrincipal(ServerWebExchange exchange, Principal principal) {
        return new ServerWebExchangeDecorator(exchange) {
            @Override
            public <T extends Principal> Mono<T> getPrincipal() {
                return Mono.just((T) principal);
            }
        };
    }

    private static final class CapturingGatewayFilterChain implements GatewayFilterChain {
        private final AtomicReference<ServerWebExchange> lastExchange = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            lastExchange.set(exchange);
            return Mono.empty();
        }

        ServerWebExchange lastExchange() {
            return lastExchange.get();
        }
    }
}
