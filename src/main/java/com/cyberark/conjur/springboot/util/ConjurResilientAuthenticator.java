package com.cyberark.conjur.springboot.util;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cyberark.conjur.sdk.AccessToken;
import com.cyberark.conjur.sdk.ApiException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Resilience4j-based authentication wrapper for Conjur.
 * Retries only on transient network failures and 5xx server errors.
 */
public class ConjurResilientAuthenticator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConjurResilientAuthenticator.class);

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ConjurResilientAuthenticator(int maxAttempts, Duration waitDuration) {
        this.circuitBreaker = CircuitBreaker.ofDefaults("conjurAuthCB");

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(waitDuration)
                .retryOnResult(result -> { 
                	// Retry on null result because the ApiClient returns null for all exception types.
                    boolean shouldRetry = result == null;
                    if (shouldRetry) {
                        LOGGER.warn("Retrying connection with conjur");
                    }
                    return shouldRetry;
                })
                .retryOnException(throwable -> {
                    Throwable cause = (throwable.getCause() != null) ? throwable.getCause() : throwable;
                    LOGGER.debug("inside retry exception: {}", cause.getClass().getName());

                    if (cause instanceof SocketTimeoutException ||
                        cause instanceof ConnectException ||
                        cause instanceof UnknownHostException) {
                        LOGGER.warn("Retrying authentication due to network error: {}", cause.getMessage());
                        return true;
                    }

                    if (cause instanceof ApiException) {

                        int code = ((ApiException) cause).getCode();
                        LOGGER.debug("inside Apiexception: {}", cause.getClass().getName(),code);

                        if (code >= 500) {
                            LOGGER.warn("Retrying authentication due to server error: HTTP {}", code);
                            return true;
                        }
                    }

                    LOGGER.debug("Not retrying on exception: {}", cause.getClass().getName());
                    return false;
                })
                .build();

        this.retry = Retry.of("conjurAuthRetry", retryConfig);

        retry.getEventPublisher()
            .onRetry(event -> LOGGER.info("Retrying authentication attempt #{} for '{}'",
                    event.getNumberOfRetryAttempts(), event.getName()));
    }

    /**
     * Executes a resilient authentication operation with retry and circuit breaker.
     *
     * @param authSupplier Supplier that performs the actual authentication logic.
     * @return AccessToken if successful
     */
    public AccessToken execute(Supplier<AccessToken> authSupplier){
        Supplier<AccessToken> decorated = CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, authSupplier));
        try {
            return decorated.get();
        } catch (Exception ex) {
            LOGGER.error("Authentication failed after retries: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to authenticate with Conjur", ex);
        }
    }
}