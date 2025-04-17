package com.cyberark.conjur.springboot.util;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ResilienceRegistryHolderTest {
    @AfterEach
    void cleanUp() {
        // Reset static holders after each test
        ResilienceRegistryHolder.setRetryRegistry(null);
        ResilienceRegistryHolder.setCircuitBreakerRegistry(null);
    }

    @Test
    void testInitializeSetsBothRegistries() {
        RetryRegistry retry = RetryRegistry.ofDefaults();
        CircuitBreakerRegistry circuitBreaker = CircuitBreakerRegistry.ofDefaults();

        ResilienceRegistryHolder.initialize(retry, circuitBreaker);

        assertSame(retry, ResilienceRegistryHolder.getRetryRegistry());
        assertSame(circuitBreaker, ResilienceRegistryHolder.getCircuitBreakerRegistry());
    }

    @Test
    void testSetAndGetRetryRegistry() {
        RetryRegistry retry = RetryRegistry.ofDefaults();
        ResilienceRegistryHolder.setRetryRegistry(retry);

        assertSame(retry, ResilienceRegistryHolder.getRetryRegistry());
    }

    @Test
    void testSetAndGetCircuitBreakerRegistry() {
        CircuitBreakerRegistry cb = CircuitBreakerRegistry.ofDefaults();
        ResilienceRegistryHolder.setCircuitBreakerRegistry(cb);

        assertSame(cb, ResilienceRegistryHolder.getCircuitBreakerRegistry());
    }

    @Test
    void testInitialRegistriesAreNull() {
        assertNull(ResilienceRegistryHolder.getRetryRegistry());
        assertNull(ResilienceRegistryHolder.getCircuitBreakerRegistry());
    }
}
