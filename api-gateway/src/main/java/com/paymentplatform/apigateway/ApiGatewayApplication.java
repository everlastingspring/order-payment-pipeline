package com.paymentplatform.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for api-gateway.
 *
 * <p>The API gateway is the single public ingress point for the platform.
 * It is built on Spring Cloud Gateway (WebFlux / Reactor Netty) and handles:</p>
 * <ul>
 *   <li><b>JWT validation</b> — {@code GatewayAuthFilter} validates Keycloak-issued tokens
 *       and forwards user identity via {@code X-User-Id} / {@code X-User-Roles} headers
 *       to downstream services (gateway-trust model).</li>
 *   <li><b>Rate limiting</b> — Redis token-bucket per user per minute.</li>
 *   <li><b>Request routing</b> — path-based routing to all 5 downstream services.</li>
 *   <li><b>Request logging</b> — structured access logs with trace IDs for observability.</li>
 * </ul>
 *
 * <p>Downstream services are only reachable via the gateway in production
 * (K8s ClusterIP / NetworkPolicy). Runs on port {@code 8080}.</p>
 */
@SpringBootApplication
public class ApiGatewayApplication {

    /**
     * Application entry point. Bootstraps the reactive Spring context and starts
     * the embedded Reactor Netty HTTP server.
     *
     * @param args command-line arguments passed through to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
