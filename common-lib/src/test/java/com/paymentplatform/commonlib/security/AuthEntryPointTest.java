package com.paymentplatform.commonlib.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthEntryPoint Tests")
class AuthEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private AuthEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        entryPoint = new AuthEntryPoint(objectMapper);
    }

    @Test
    @DisplayName("commence writes 401 JSON error body")
    void commence_writesUnauthorizedResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("\"error\":\"Unauthorized\"");
        assertThat(response.getContentAsString()).contains("Authentication required");
        assertThat(response.getContentAsString()).contains("\"path\":\"/api/orders\"");
        assertThat(response.getContentAsString()).contains("\"timestamp\"");
    }
}
