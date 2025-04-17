package com.cyberark.conjur.springboot.util;

import com.cyberark.conjur.sdk.AccessToken;
import com.cyberark.conjur.sdk.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class ConjurResilientAuthenticatorTest {

    private ConjurResilientAuthenticator authenticator;

    @BeforeEach
    void setup() {
        authenticator = new ConjurResilientAuthenticator(3, Duration.ofMillis(10));
    }

    @Test
    void testSuccessfulAuthenticationWithoutRetry() {
        AccessToken expectedToken = new AccessToken();
        Supplier<AccessToken> supplier = () -> expectedToken;

        AccessToken result = authenticator.execute(supplier);

        assertEquals(expectedToken, result);
    }

    @Test
    void testAuthenticationRetriesOnNullResult() {
        AtomicInteger attemptCounter = new AtomicInteger();
        Supplier<AccessToken> supplier = () -> {
            if (attemptCounter.incrementAndGet() < 3) {
                return null; // simulate transient failure
            }
            return new AccessToken();
        };

        AccessToken token = authenticator.execute(supplier);
        assertNotNull(token);
        assertEquals(3, attemptCounter.get());
    }

    @Test
    void testRetriesOnSocketTimeoutException() {
        AtomicInteger counter = new AtomicInteger();

        Supplier<AccessToken> supplier = () -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException(new SocketTimeoutException("Timeout"));
            }
            return new AccessToken();
        };

        AccessToken result = authenticator.execute(supplier);
        assertNotNull(result);
        assertEquals(3, counter.get());
    }

    @Test
    void testRetriesOnConnectException() {
        AtomicInteger counter = new AtomicInteger();

        Supplier<AccessToken> supplier = () -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException(new ConnectException("Connection failed"));
            }
            return new AccessToken();
        };

        AccessToken result = authenticator.execute(supplier);
        assertNotNull(result);
        assertEquals(3, counter.get());
    }

    @Test
    void testRetriesOnUnknownHostException() {
        AtomicInteger counter = new AtomicInteger();

        Supplier<AccessToken> supplier = () -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException(new UnknownHostException("Host not found"));
            }
            return new AccessToken();
        };

        AccessToken result = authenticator.execute(supplier);
        assertNotNull(result);
        assertEquals(3, counter.get());
    }

    @Test
    void testRetriesOnApiExceptionWith5xx() {
        AtomicInteger counter = new AtomicInteger();

        Supplier<AccessToken> supplier = () -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException(new ApiException(503, "Service Unavailable"));
            }
            return new AccessToken();
        };

        AccessToken result = authenticator.execute(supplier);
        assertNotNull(result);
        assertEquals(3, counter.get());
    }

    @Test
    void testNoRetryOnApiExceptionWith4xx() {
        Supplier<AccessToken> supplier = () -> {
            throw new RuntimeException(new ApiException(403, "Forbidden"));
        };

        RuntimeException exception = assertThrows(RuntimeException.class, () -> authenticator.execute(supplier));
        assertTrue(exception.getMessage().contains("Failed to authenticate with Conjur"));
    }

    @Test
    void testFailsAfterExceedingRetries() {
        AtomicInteger counter = new AtomicInteger();

        Supplier<AccessToken> supplier = () -> {
            counter.incrementAndGet();
            throw new RuntimeException(new SocketTimeoutException("Always fail"));
        };

        RuntimeException exception = assertThrows(RuntimeException.class, () -> authenticator.execute(supplier));
        assertTrue(exception.getMessage().contains("Failed to authenticate with Conjur"));
        assertEquals(3, counter.get()); // max attempts
    }
}
