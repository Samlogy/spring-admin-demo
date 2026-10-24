package com.example.monolith.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ActuatorConfig {

    /**
     * Backs /actuator/httpexchanges. Kept in memory with a bounded size; disabled in prod
     * ({@code app.actuator.http-exchanges.enabled=false}) because it stores request metadata.
     */
    @Bean
    @ConditionalOnBooleanProperty("app.actuator.http-exchanges.enabled")
    InMemoryHttpExchangeRepository httpExchangeRepository(
            @Value("${app.actuator.http-exchanges.capacity:100}") int capacity) {
        InMemoryHttpExchangeRepository repository = new InMemoryHttpExchangeRepository();
        repository.setCapacity(capacity);
        return repository;
    }

    /**
     * {@code show-values: when-authorized} would reveal every value to the Actuator account;
     * keep secret-like keys (password, secret, token, key, credentials...) and URIs with
     * embedded credentials masked anyway.
     */
    @Bean
    SanitizingFunction secretSanitizer() {
        return SanitizingFunction.sanitizeValue().ifLikelySensitive();
    }
}
