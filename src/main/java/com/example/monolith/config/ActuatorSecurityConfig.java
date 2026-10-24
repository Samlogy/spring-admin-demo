package com.example.monolith.config;

import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures the Actuator endpoints for Spring Boot Admin.
 * <p>
 * Only {@code health} and {@code info} are public (health details still need the ACTUATOR role).
 * Every other endpoint needs HTTP Basic with the technical Actuator account
 * ({@code ACTUATOR_USER}/{@code ACTUATOR_PASSWORD}, see {@code spring.security.user.*}), which the
 * Admin Server receives through the instance metadata {@code user.name}/{@code user.password}.
 */
@Configuration(proxyBeanMethods = false)
public class ActuatorSecurityConfig {

    public static final String ACTUATOR_ROLE = "ACTUATOR";

    @Bean
    @Order(1)
    SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                        .anyRequest().hasRole(ACTUATOR_ROLE))
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Stateless, Basic-auth machine-to-machine calls only (no browser session to ride on),
                // so CSRF protection does not apply; needed for POST /actuator/loggers/{name}.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /**
     * Business endpoints keep the behaviour they had before Spring Security was added (no auth).
     * In a monolith that already has a SecurityFilterChain, drop this bean and keep the existing one:
     * the {@code @Order(1)} Actuator chain above takes precedence for Actuator requests only.
     */
    @Bean
    @Order(2)
    SecurityFilterChain applicationSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
