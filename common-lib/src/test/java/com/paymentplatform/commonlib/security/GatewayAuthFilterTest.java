package com.paymentplatform.commonlib.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatewayAuthFilter Tests")
class GatewayAuthFilterTest {

    private final GatewayAuthFilter filter = new GatewayAuthFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("filter builds authentication from gateway headers and clears context after request")
    void doFilter_setsAuthenticationFromHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GatewayAuthConstants.HEADER_USER_ID, "user-123");
        request.addHeader(GatewayAuthConstants.HEADER_USER_ROLES, "admin, user ,,ops");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Authentication> authenticationInChain = new AtomicReference<>();

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException, jakarta.servlet.ServletException {
                authenticationInChain.set(SecurityContextHolder.getContext().getAuthentication());
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(authenticationInChain.get()).isNotNull();
        assertThat(authenticationInChain.get().getName()).isEqualTo("user-123");
        assertThat(authenticationInChain.get().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_admin", "ROLE_user", "ROLE_ops");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("filter leaves context empty when user header is missing")
    void doFilter_skipsAuthenticationWithoutUserId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GatewayAuthConstants.HEADER_USER_ROLES, "admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Authentication> authenticationInChain = new AtomicReference<>();

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException, jakarta.servlet.ServletException {
                authenticationInChain.set(SecurityContextHolder.getContext().getAuthentication());
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(authenticationInChain.get()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
