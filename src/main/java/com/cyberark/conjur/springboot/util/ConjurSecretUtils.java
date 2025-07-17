package com.cyberark.conjur.springboot.util;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cyberark.conjur.sdk.ApiException;
import com.cyberark.conjur.sdk.endpoint.SecretsApi;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Utility class to securely fetch secrets from Conjur with optional Resilience4j-based
 * retry and circuit breaker support. Supports both single and bulk secret retrieval.
 */
public class ConjurSecretUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConjurSecretUtils.class);

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final boolean resilienceEnabled;

    /**
     * Constructor used internally.
     */
    public ConjurSecretUtils(boolean resilienceEnabled, CircuitBreaker circuitBreaker, Retry retry) {
        this.resilienceEnabled = resilienceEnabled;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    /**
     * Static factory method to create ConjurSecretUtils with resilience enabled or
     * disabled.
     */
    public static ConjurSecretUtils create(boolean resilienceEnabled, int maxAttempts, Duration waitDuration) {
        {
            if (!resilienceEnabled) {
                return new ConjurSecretUtils(false, null, null);
            }


            CircuitBreakerRegistry cbRegistry = ResilienceRegistryHolder.getCircuitBreakerRegistry();
            RetryRegistry retryRegistry = ResilienceRegistryHolder.getRetryRegistry();

            if (cbRegistry == null) {
                cbRegistry = CircuitBreakerRegistry.ofDefaults();
                ResilienceRegistryHolder.setCircuitBreakerRegistry(cbRegistry);
            }
            if (retryRegistry == null) {
                retryRegistry = RetryRegistry.ofDefaults();
                ResilienceRegistryHolder.setRetryRegistry(retryRegistry);
            }

            CircuitBreakerConfig cbConfig = cbRegistry.getConfiguration("conjurSecretCB")
                    .orElse(CircuitBreakerConfig.ofDefaults());

            CircuitBreaker cb = cbRegistry.circuitBreaker("conjurSecretCB", cbConfig);

            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(maxAttempts)
                    .waitDuration(waitDuration)
                    .retryOnException(throwable -> {
                        Throwable cause = throwable;
                        if (throwable instanceof RuntimeException && throwable.getCause() != null) {
                            cause = throwable.getCause();
                        }

                        // Retry ONLY on network issues, NOT on ApiException
                        if (cause instanceof java.net.SocketTimeoutException ||
                                cause instanceof java.net.ConnectException ||
                                cause instanceof java.net.UnknownHostException) {
                            LOGGER.info("Retrying - network issue: {}", cause.getMessage());
                            return true;
                        }

                        //Do not retry for ApiException or other handled errors
                        if (cause instanceof ApiException) {
                            ApiException apiEx = (ApiException) cause;
                            int code = apiEx.getCode();
                            String message = apiEx.getMessage() != null ? apiEx.getMessage().toLowerCase() : "";

                            LOGGER.warn("No retry - received API response from Conjur (code={}): {}", code, message);
                            return false;
                        }

                        // Default: no retry
                        LOGGER.debug("Not retrying - exception type not configured for retry: {}", cause.getClass().getName());
                        return false;
                    })
                    .build();

            Retry retry = Retry.of("fetchSecretRetry", retryConfig);

            retry.getEventPublisher()
                    .onRetry(event -> LOGGER.info("Retry attempt #{} for operation {}", event.getNumberOfRetryAttempts(), event.getName()));

            return new ConjurSecretUtils(true, cb, retry);
        }
    }

    /**
     * Fetch a single secret, optionally using resilience4j if enabled.
     */
    public byte[] fetchSecret(SecretsApi secretsApi, String account, String kind, String key) {
        if (!resilienceEnabled || circuitBreaker == null || retry == null) {
            return fetchSecretRegular(secretsApi, account, kind, key);
        }

        Supplier<byte[]> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, () -> fetchSecretRegular(secretsApi, account, kind, key)));

        LOGGER.debug("Executing fetchSecret with circuit breaker and retry enabled for key '{}'.", key);
        LOGGER.debug("Circuit Breaker State: {}, Failure Rate: {}%, Failed Calls with Retry: {}",
                circuitBreaker.getState(),
                circuitBreaker.getMetrics().getFailureRate(),
                retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt());

        try {
            byte[] result = decoratedSupplier.get();
            LOGGER.info("Successfully fetched secret for key '{}'.", key);
            return result;
        } catch (Exception throwable) {
            LOGGER.error("Resilience-based fetch failed for secret key '{}'. Reason: {}", key, throwable.getMessage(), throwable);
            throw new IllegalStateException("Failed to fetch secret with resilience for key: " + key, throwable);
        }
    }


    /**
     * Internal method for fetching a secret without resilience mechanisms (no retry or circuit breaker).
     */
    private byte[] fetchSecretRegular(SecretsApi secretsApi, String account, String kind, String key) {
        try {
            String secret = secretsApi.getSecret(account, kind, key);

            if (secret != null) {
                LOGGER.info("Successfully fetched secret for key '{}'.", key);
                return secret.getBytes(StandardCharsets.UTF_8);
            } else {
                LOGGER.warn("Secret returned as null for key '{}'.", key);
                return null;
            }
        } catch (ApiException e) {
            LOGGER.warn("API exception while fetching secret for key '{}': {}", key, e.getMessage(), e);

            if (resilienceEnabled) {
                throw new RuntimeException("Failed to fetch secret from Conjur for key: " + key, e);
            } else {
                return null;
            }
        }
    }

    /**
     * Fetch multiple secrets in bulk, fallback to individual fetch on failure.
     */
    public byte[] fetchSecrets(SecretsApi secretsApi, String fullKeys, Gson gson) throws ApiException {
        List<byte[]> secretList = new ArrayList<>();
        try {
            String bulkSecretsJson = gson.toJson(secretsApi.getSecrets(fullKeys));
            String processedSecrets = processMultipleSecretResult(bulkSecretsJson);
            return processedSecrets.getBytes(StandardCharsets.UTF_8);
        } catch (ApiException ex) {
            handleBulkSecretFailure(ex, fullKeys.split(","), secretsApi, secretList);

            String secretOutput = secretList.stream().map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                    .collect(Collectors.joining(","));

            return secretOutput.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Handles fallback for bulk fetch failure by fetching secrets individually.
     */
    private void handleBulkSecretFailure(ApiException ex, String[] keys, SecretsApi secretsApi, List<byte[]> secretList)
            throws ApiException {
        if (ex.getCode() == 404 || "Not Found".equalsIgnoreCase(ex.getMessage())) {
            LOGGER.warn("Bulk fetch failed with 404. Falling back to individual secret fetch.");
            for (String fullKey : keys) {
                try {
                    String[] parts = fullKey.trim().split(":", 3);
                    if (parts.length != 3) {
                        LOGGER.warn("Invalid secret format: {}", fullKey);
                        continue;
                    }

                    String account = parts[0];
                    String kind = parts[1];
                    String path = parts[2];
                    byte[] secret = fetchSecret(secretsApi, account, kind, path);

                    if (secret != null) {
                        String value = new String(secret, StandardCharsets.UTF_8);
                        String keyName = path.substring(path.lastIndexOf('/') + 1);
                        String entry = keyName + "=" + value;
                        secretList.add(entry.getBytes(StandardCharsets.UTF_8));
                    } else {
                        LOGGER.warn("Secret not found or null for key: {}", fullKey);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to fetch individual secret for key: {}", fullKey, e);
                }
            }
        } else {
            throw ex;
        }
    }

    /**
     * Converts bulk fetch JSON response into "key=value" comma-separated string.
     */
    private String processMultipleSecretResult(String json) {
        Map<String, String> secretMap = new Gson().fromJson(json, new TypeToken<Map<String, String>>() {
        }.getType());
        return secretMap.entrySet().stream().map(e -> {
            String[] keyParts = e.getKey().split("/");
            String finalKey = keyParts[keyParts.length - 1];
            return finalKey + "=" + e.getValue();
        }).reduce((a, b) -> a + "," + b).orElse("");
    }

}
