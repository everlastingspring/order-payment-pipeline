package com.paymentplatform.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequestLoggingFilter Tests")
class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;
    private CapturingGatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        chain = new CapturingGatewayFilterChain();
    }

    @Nested
    @DisplayName("Request Logging")
    class RequestLoggingTests {
        @Test
        @DisplayName("should process GET requests and pass to chain")
        void filter_processesGetRequest() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/orders").build()
            );

            filter.filter(exchange, chain).block();

            assertThat(chain.wasInvoked()).isTrue();
            assertThat(chain.lastExchange().getRequest().getMethod()).isEqualTo(HttpMethod.GET);
            assertThat(chain.lastExchange().getRequest().getURI().getPath()).isEqualTo("/api/orders");
        }

        @Test
        @DisplayName("should process POST requests with request body")
        void filter_processesPostRequest() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/orders")
                            .header("Content-Type", "application/json")
                            .build()
            );

            filter.filter(exchange, chain).block();

            assertThat(chain.wasInvoked()).isTrue();
            assertThat(chain.lastExchange().getRequest().getMethod()).isEqualTo(HttpMethod.POST);
        }

        @Test
        @DisplayName("should handle requests from different HTTP methods")
        void filter_handlesDifferentMethods() {
            HttpMethod[] methods = {
                    HttpMethod.GET,
                    HttpMethod.POST,
                    HttpMethod.PUT,
                    HttpMethod.PATCH,
                    HttpMethod.DELETE
            };

            for (HttpMethod method : methods) {
                MockServerWebExchange exchange = MockServerWebExchange.from(
                        MockServerHttpRequest.method(method, "/api/resource/123").build()
                );

                filter.filter(exchange, chain).block();

                assertThat(chain.lastExchange().getRequest().getMethod()).isEqualTo(method);
            }
        }

        @Test
        @DisplayName("should preserve X-Forwarded-For header")
        void filter_extractsClientIpFromHeader() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/orders")
                    .header("X-Forwarded-For", "192.168.1.100")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            filter.filter(exchange, chain).block();

            assertThat(chain.lastExchange().getRequest().getHeaders().getFirst("X-Forwarded-For"))
                    .isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should keep multiple IPs in X-Forwarded-For unchanged")
        void filter_handlesMultipleIpsInHeader() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/payments")
                    .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1, 172.16.0.1")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            filter.filter(exchange, chain).block();

            assertThat(chain.lastExchange().getRequest().getHeaders().getFirst("X-Forwarded-For"))
                    .isEqualTo("192.168.1.100, 10.0.0.1, 172.16.0.1");
        }

        @Test
        @DisplayName("should complete the chain successfully")
        void filter_completesChain() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/wallets").build()
            );

            Mono<Void> result = filter.filter(exchange, chain);
            result.block();

            assertThat(result).isNotNull();
            assertThat(chain.wasInvoked()).isTrue();
        }

        @Test
        @DisplayName("should preserve response status after chain completion")
        void filter_preservesResponseStatus() {
            chain.setResponseStatus(HttpStatus.ACCEPTED);
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/orders").build()
            );

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("Filter Ordering")
    class FilterOrderingTests {
        @Test
        @DisplayName("should have HIGHEST_PRECEDENCE order to run first")
        void filter_hasHighestPrecedence() {
            assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        }

        @Test
        @DisplayName("should run before later ordered filters")
        void filter_runsBeforeOtherFilters() {
            assertThat(filter.getOrder()).isLessThan(1);
        }
    }

    private static final class CapturingGatewayFilterChain implements GatewayFilterChain {
        private final AtomicReference<ServerWebExchange> lastExchange = new AtomicReference<>();
        private HttpStatus responseStatus;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            lastExchange.set(exchange);
            if (responseStatus != null) {
                exchange.getResponse().setStatusCode(responseStatus);
            }
            return Mono.empty();
        }

        boolean wasInvoked() {
            return lastExchange.get() != null;
        }

        ServerWebExchange lastExchange() {
            return lastExchange.get();
        }

        void setResponseStatus(HttpStatus responseStatus) {
            this.responseStatus = responseStatus;
        }
    }
}
