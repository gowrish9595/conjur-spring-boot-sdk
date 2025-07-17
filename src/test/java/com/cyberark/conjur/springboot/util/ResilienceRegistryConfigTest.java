package com.cyberark.conjur.springboot.util;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ResilienceRegistryConfigTest {
    private ResilienceRegistryConfig config;

    @BeforeEach
    void setUp() {
        // Reset static holder before each test
        ResilienceRegistryHolder.setCircuitBreakerRegistry(null);
        ResilienceRegistryHolder.setRetryRegistry(null);

        config = new ResilienceRegistryConfig();
    }

    @AfterEach
    void tearDown() {
        // Clean up to avoid state leakage
        ResilienceRegistryHolder.setCircuitBreakerRegistry(null);
        ResilienceRegistryHolder.setRetryRegistry(null);
    }

    @Test
    void testInitSetsRegistriesIfNull() {
        assertNull(ResilienceRegistryHolder.getCircuitBreakerRegistry());
        assertNull(ResilienceRegistryHolder.getRetryRegistry());

        config.init();

        assertNotNull(ResilienceRegistryHolder.getCircuitBreakerRegistry());
        assertNotNull(ResilienceRegistryHolder.getRetryRegistry());
    }

    @Test
    void testInitDoesNotOverrideIfAlreadySet() {
        CircuitBreakerRegistry mockCB = CircuitBreakerRegistry.ofDefaults();
        RetryRegistry mockRetry = RetryRegistry.ofDefaults();

        ResilienceRegistryHolder.setCircuitBreakerRegistry(mockCB);
        ResilienceRegistryHolder.setRetryRegistry(mockRetry);

        config.init();  // should not override existing values

        assertSame(mockCB, ResilienceRegistryHolder.getCircuitBreakerRegistry());
        assertSame(mockRetry, ResilienceRegistryHolder.getRetryRegistry());
    }

    @Test
    void testBootstrapInitializesIfNull() {
        assertNull(ResilienceRegistryHolder.getCircuitBreakerRegistry());
        assertNull(ResilienceRegistryHolder.getRetryRegistry());

        ResilienceRegistryConfig.bootstrap();

        assertNotNull(ResilienceRegistryHolder.getCircuitBreakerRegistry());
        assertNotNull(ResilienceRegistryHolder.getRetryRegistry());
    }

    @Test
    void testBootstrapDoesNotOverrideIfAlreadySet() {
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();

        ResilienceRegistryHolder.setCircuitBreakerRegistry(cbRegistry);
        ResilienceRegistryHolder.setRetryRegistry(retryRegistry);

        ResilienceRegistryConfig.bootstrap();  // should not override

        assertSame(cbRegistry, ResilienceRegistryHolder.getCircuitBreakerRegistry());
        assertSame(retryRegistry, ResilienceRegistryHolder.getRetryRegistry());
    }

    @Test
    void testCircuitBreakerRegistryBeanReturnsDefaultInstance() {
        CircuitBreakerRegistry cbRegistry = config.circuitBreakerRegistry();
        assertNotNull(cbRegistry);
    }

    @Test
    void testRetryRegistryBeanReturnsDefaultInstance() {
        RetryRegistry retryRegistry = config.retryRegistry();
        assertNotNull(retryRegistry);
    }
}
