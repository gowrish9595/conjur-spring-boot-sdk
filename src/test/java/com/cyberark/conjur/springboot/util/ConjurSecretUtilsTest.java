package com.cyberark.conjur.springboot.util;

import com.cyberark.conjur.sdk.ApiException;
import com.cyberark.conjur.sdk.endpoint.SecretsApi;
import com.google.gson.Gson;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ConjurSecretUtilsTest {

    @Mock
    private SecretsApi secretsApiMock;

    private ConjurSecretUtils conjurSecretUtils;
    private Gson gson;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Use ResilienceRegistryHolder to get or create registries like in your create() method
        CircuitBreakerRegistry cbRegistry = ResilienceRegistryHolder.getCircuitBreakerRegistry();
        if (cbRegistry == null) {
            cbRegistry = CircuitBreakerRegistry.ofDefaults();
            ResilienceRegistryHolder.setCircuitBreakerRegistry(cbRegistry);
        }

        RetryRegistry retryRegistry = ResilienceRegistryHolder.getRetryRegistry();
        if (retryRegistry == null) {
            retryRegistry = RetryRegistry.ofDefaults();
            ResilienceRegistryHolder.setRetryRegistry(retryRegistry);
        }

        CircuitBreakerConfig cbConfig = cbRegistry.getConfiguration("conjurSecretCB")
            .orElse(CircuitBreakerConfig.ofDefaults());
        CircuitBreaker circuitBreaker = cbRegistry.circuitBreaker("conjurSecretCB", cbConfig);

        RetryConfig retryConfig = retryRegistry.getConfiguration("fetchSecretRetry")
            .orElse(RetryConfig.ofDefaults());
        Retry retry = Retry.of("fetchSecretRetry", retryConfig);

        conjurSecretUtils = new ConjurSecretUtils(true, circuitBreaker, retry);
        gson = new Gson();
    }


    @Test
    public void testFetchSecret_withResilienceEnabled_shouldReturnSecretBytes() throws ApiException {
        String account = "myAccount";
        String kind = "variable";
        String key = "db/dbpassWord";
        String mockSecret = "mockedSecret";

        when(secretsApiMock.getSecret(account, kind, key)).thenReturn(mockSecret);

        byte[] result = conjurSecretUtils.fetchSecret(secretsApiMock, account, kind, key);
        byte[] expected = mockSecret.getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(expected, result);
        verify(secretsApiMock, times(1)).getSecret(account, kind, key);
    }

    @Test
    public void testFetchSecret_withException_shouldThrowIllegalStateException() throws ApiException {
        String account = "myAccount";
        String kind = "variable";
        String key = "db/dbpassWord";

        when(secretsApiMock.getSecret(account, kind, key))
            .thenThrow(new ApiException(500, "Internal Server Error"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            conjurSecretUtils.fetchSecret(secretsApiMock, account, kind, key);
        });

        assertTrue(thrown.getMessage().contains("Failed to fetch secret"));
    }

    @Test
    void testFetchSecretWithoutResilience() throws Exception {
        String account = "dev";
        String kind = "variable";
        String key = "path/to/secret";
        String secretValue = "mySecret";
        ResilienceRegistryHolder.setCircuitBreakerRegistry(null);
        ResilienceRegistryHolder.setRetryRegistry(null);
        when(secretsApiMock.getSecret(account, kind, key)).thenReturn(secretValue);

        ConjurSecretUtils utils = ConjurSecretUtils.create(false, 3, Duration.ofMillis(10));
        byte[] result = utils.fetchSecret(secretsApiMock, account, kind, key);

        assertNotNull(result);
        assertEquals(secretValue, new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void testFetchSecretReturnsNullWithoutResilienceOnApiException() throws Exception {
        String account = "dev";
        String kind = "variable";
        String key = "path/to/secret";
        String secretValue = "mySecret";
        ResilienceRegistryHolder.setCircuitBreakerRegistry(null);
        ResilienceRegistryHolder.setRetryRegistry(null);
        when(secretsApiMock.getSecret(account, kind, key)).thenThrow(new ApiException(404, "Not Found"));

        ConjurSecretUtils utils = ConjurSecretUtils.create(false, 3, Duration.ofMillis(10));
        byte[] result = utils.fetchSecret(secretsApiMock, account, kind, key);

        assertNull(result);
    }

    @Test
    void testFetchSecretWithResilienceSuccess() throws Exception {
        String account = "dev";
        String kind = "variable";
        String key = "path/to/secret";
        String secretValue = "mySecret";
        ResilienceRegistryHolder.setCircuitBreakerRegistry(CircuitBreakerRegistry.ofDefaults());
        ResilienceRegistryHolder.setRetryRegistry(RetryRegistry.ofDefaults());

        when(secretsApiMock.getSecret(account, kind, key)).thenReturn(secretValue);

        ConjurSecretUtils utils = ConjurSecretUtils.create(true, 3, Duration.ofMillis(10));
        byte[] result = utils.fetchSecret(secretsApiMock, account, kind, key);

        assertNotNull(result);
        assertEquals(secretValue, new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void testFetchSecretWithResilienceFailureThrows() throws ApiException {
        String account = "dev";
        String kind = "variable";
        String key = "path/to/secret";
        String secretValue = "mySecret";
        ResilienceRegistryHolder.setCircuitBreakerRegistry(CircuitBreakerRegistry.ofDefaults());
        ResilienceRegistryHolder.setRetryRegistry(RetryRegistry.ofDefaults());

        when(secretsApiMock.getSecret(account, kind, key)).thenThrow(new ApiException(500, "Internal Error"));

        ConjurSecretUtils utils = ConjurSecretUtils.create(true, 2, Duration.ofMillis(10));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> utils.fetchSecret(secretsApiMock, account, kind, key));
        assertTrue(ex.getMessage().contains("Failed to fetch secret"));
    }

    @Test
    void testFetchSecretsBulkSuccess() throws Exception {
        Map<String, String> secretMap = new HashMap<>();
        secretMap.put("dev/variable/db/password", "admin123");
        secretMap.put("dev/variable/db/username", "admin");

        when(secretsApiMock.getSecrets("dev:variable:db/password,dev:variable:db/username"))
            .thenReturn(secretMap);

        ConjurSecretUtils utils = ConjurSecretUtils.create(false, 3, Duration.ofMillis(10));
        byte[] result = utils.fetchSecrets(secretsApiMock, "dev:variable:db/password,dev:variable:db/username", gson);

        assertNotNull(result);
        String str = new String(result, StandardCharsets.UTF_8);
        assertTrue(str.contains("username=admin"));
        assertTrue(str.contains("password=admin123"));
    }

    @Test
    void testFetchSecretsFallbackToSingle() throws Exception {
        String fullKeys = "dev:variable:path/to/key1,dev:variable:path/to/key2";
        when(secretsApiMock.getSecrets(fullKeys)).thenThrow(new ApiException(404, "Not Found"));
        when(secretsApiMock.getSecret("dev", "variable", "path/to/key1")).thenReturn("value1");
        when(secretsApiMock.getSecret("dev", "variable", "path/to/key2")).thenReturn("value2");

        ConjurSecretUtils utils = ConjurSecretUtils.create(false, 2, Duration.ofMillis(10));
        byte[] result = utils.fetchSecrets(secretsApiMock, fullKeys, gson);

        String resultStr = new String(result, StandardCharsets.UTF_8);
        assertTrue(resultStr.contains("key1=value1"));
        assertTrue(resultStr.contains("key2=value2"));
    }

    @Test
    void testFetchSecretsFallbackSkipsInvalidKeys() throws Exception {
        String fullKeys = "invalidkeyformat,dev:variable:valid/key";
        when(secretsApiMock.getSecrets(fullKeys)).thenThrow(new ApiException(404, "Not Found"));
        when(secretsApiMock.getSecret("dev", "variable", "valid/key")).thenReturn("value");

        ConjurSecretUtils utils = ConjurSecretUtils.create(false, 2, Duration.ofMillis(10));
        byte[] result = utils.fetchSecrets(secretsApiMock, fullKeys, gson);

        String resultStr = new String(result, StandardCharsets.UTF_8);
        assertTrue(resultStr.contains("key=value"));
    }


}
