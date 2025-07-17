package com.cyberark.conjur.springboot.util;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Holds global static references to Resilience4j's RetryRegistry and CircuitBreakerRegistry.
 * Used to provide centralized access to retry and circuit breaker configurations.
 */
public class ResilienceRegistryHolder {

    private static RetryRegistry retryRegistry;
    private static CircuitBreakerRegistry circuitBreakerRegistry;

    public static void initialize(RetryRegistry retryReg, CircuitBreakerRegistry cbReg) {
        retryRegistry = retryReg;
        circuitBreakerRegistry = cbReg;
    }

    public static void setRetryRegistry(RetryRegistry retryRegistry) {
		ResilienceRegistryHolder.retryRegistry = retryRegistry;
	}

	public static void setCircuitBreakerRegistry(CircuitBreakerRegistry circuitBreakerRegistry) {
		ResilienceRegistryHolder.circuitBreakerRegistry = circuitBreakerRegistry;
	}

	public static RetryRegistry getRetryRegistry() {
        return retryRegistry;
    }

    public static CircuitBreakerRegistry getCircuitBreakerRegistry() {
        return circuitBreakerRegistry;
    }
}

