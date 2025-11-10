package com.selimhorri.app.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

/**
 * Test configuration for RestTemplate without Load Balancer.
 * This configuration provides a plain RestTemplate for integration tests
 * that use WireMock to mock external service calls.
 */
@TestConfiguration
@Profile("test")
public class TestRestTemplateConfig {

    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
