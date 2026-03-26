package com.paymentplatform.commonlib.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthAccessDeniedHandler Tests")
class AuthAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private AuthAccessDeniedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuthAccessDeniedHandler(objectMapper);
    }

    @Test
    @DisplayName("handle writes 403 JSON error body")
    void handle_writesForbiddenResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("forbidden"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("\"error\":\"Forbidden\"");
        assertThat(response.getContentAsString()).contains("do not have permission");
        assertThat(response.getContentAsString()).contains("\"path\":\"/api/admin\"");
        assertThat(response.getContentAsString()).contains("\"timestamp\"");
    }
}
