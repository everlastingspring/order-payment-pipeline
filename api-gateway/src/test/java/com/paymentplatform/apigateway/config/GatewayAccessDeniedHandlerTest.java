package com.paymentplatform.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatewayAccessDeniedHandler Tests")
class GatewayAccessDeniedHandlerTest {

    private GatewayAccessDeniedHandler handler;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        handler = new GatewayAccessDeniedHandler(objectMapper);
    }

    @Test
    @DisplayName("should return 403 Forbidden status code on access denied")
    void handle_returns403() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/admin/users").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        handler.handle(exchange, new AccessDeniedException("User lacks ADMIN role")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("should set JSON content type")
    void handle_setsJsonContentType() {
        MockServerHttpRequest request = MockServerHttpRequest.delete("/api/admin/config").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        handler.handle(exchange, new AccessDeniedException("Only admins can delete")).block();

        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("should handle different access denied scenarios")
    void handle_handlesDifferentScenarios() {
        // Scenario 1: Role-based denial
        MockServerHttpRequest request1 = MockServerHttpRequest.get("/api/reports/admin").build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        handler.handle(exchange1, new AccessDeniedException("ROLE_ADMIN required")).block();
        assertThat(exchange1.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Scenario 2: Scope-based denial
        MockServerHttpRequest request2 = MockServerHttpRequest.patch("/api/users/settings").build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        handler.handle(exchange2, new AccessDeniedException("write:scope required")).block();
        assertThat(exchange2.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
