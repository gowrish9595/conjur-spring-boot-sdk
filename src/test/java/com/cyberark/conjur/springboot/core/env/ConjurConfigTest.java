package com.cyberark.conjur.springboot.core.env;

import com.cyberark.conjur.sdk.ApiException;
import com.cyberark.conjur.springboot.annotations.ConjurPropertySource;
import com.cyberark.conjur.springboot.domain.ConjurProperties;
import com.cyberark.conjur.springboot.processor.SpringBootConjurAutoConfiguration;
import com.cyberark.conjur.springboot.util.ConjurSecretUtils;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author bnasslahsen
 */
@SpringBootTest(classes = SpringBootConjurAutoConfiguration.class)
@ConjurPropertySource(value = {"jenkins-app/"})
public class ConjurConfigTest {

    @Autowired
    private ConjurConfig conjurConfig;

    @MockBean
    private ConjurSecretUtils conjurSecretUtils;

    @Mock
    private BindResult<ConjurProperties> mockBindResult;

    @InjectMocks
    private ConjurConfig conjurConfigMock;

    private Properties props;

    @Mock
    private Environment environment;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        props = new Properties();
        conjurConfig = new ConjurConfig();
        conjurConfigMock.setEnvironment(environment);

    }

    @Test
    public void testGetMappings() throws ApiException {
        assertEquals("vault/bnl-k8s-safe/mysql-test-db/dsn", conjurConfig.mapProperty("testUrl"));
        assertEquals("vault/bnl-k8s-safe/mysql-test-db/username", conjurConfig.mapProperty("testUsername"));
    }

    @Test
    void testLoadMappingPropsWithValidPath() throws Exception {
        String validPath = "/Users/madhavi.nagilla/CONJUR-SDK-May2024/conjur-spring-boot-sdk/src/test/resources/mapping-dir/conjur.properties";

        ConjurProperties conjurProperties = mock(ConjurProperties.class);
        when(conjurProperties.getMappingPath()).thenReturn(validPath);
        when(mockBindResult.get()).thenReturn(conjurProperties);

        assertEquals(validPath, conjurProperties.getMappingPath());

    }

    @Test
    void testLoadMappingPropsFallbackToApplicationProperties() {
        ConjurConfig conjurConfig = new ConjurConfig();

        // Mock ConjurProperties with null mappingPath to trigger fallback
        ConjurProperties conjurProperties = mock(ConjurProperties.class);
        when(conjurProperties.getMappingPath()).thenReturn(null);

        BindResult<ConjurProperties> mockBindResult = mock(BindResult.class);
        when(mockBindResult.isBound()).thenReturn(true);
        when(mockBindResult.get()).thenReturn(conjurProperties);

        conjurConfig.loadMappingProps(mockBindResult);

        // assumes key is "conjur.mapping.appKey" in application.properties
        String mapped = conjurConfig.mapProperty("appKey");
        assertEquals("app/value/from/application", mapped);
    }

    @Test
    void testWaitDuration_FromMapping_Seconds() {
        setMapping("wait-duration", "5s");
        assertEquals(Duration.ofSeconds(5), conjurConfig.getResilienceWaitDuration());
    }

    @Test
    void testWaitDuration_FromMapping_MilliSeconds() {
        setMapping("wait-duration", "5ms");
        assertEquals(Duration.ofMillis(5), conjurConfig.getResilienceWaitDuration());
    }

    @Test
    void testWaitDuration_FromMapping_Minutes() {
        setMapping("wait-duration", "1m");
        assertEquals(Duration.ofMinutes(1), conjurConfig.getResilienceWaitDuration());
    }

    @Test
    void testWaitDuration_FromMapping_Hours() {
        setMapping("wait-duration", "2h");
        assertEquals(Duration.ofHours(2), conjurConfig.getResilienceWaitDuration());
    }

    @Test
    void testWaitDuration_FromMappingDays() {
        setMapping("wait-duration", "2d");
        assertEquals(Duration.ofDays(2), conjurConfig.getResilienceWaitDuration());
    }

    @Test
    void testWaitDuration_FromDefaultMapping_Seconds() {
        setMapping("wait-duration", "5");
        assertEquals(Duration.ofSeconds(5), conjurConfig.getResilienceWaitDuration());
    }

    @Test
    void testWaitDuration_DefaultWhenNotSet() {
        assertEquals(Duration.ofMillis(500), conjurConfig.getResilienceWaitDuration());
    }

    @Test
    void testMaxAttempts_FromMapping() {
        setMapping("max-attempts", "7");
        assertEquals(7, conjurConfig.getResilienceMaxAttempts());
    }

    @Test
    void testMaxAttempts_DefaultWhenNotSet() {
        assertEquals(3, conjurConfig.getResilienceMaxAttempts());
    }

    @Test
    void testResilienceEnabled_FromMapping() {
        setMapping("enabled", "false");
        assertFalse(conjurConfig.getResilienceEnabled());
    }

    @Test
    void testResilienceEnabled_DefaultTrueWhenNotSet() {
        assertTrue(conjurConfig.getResilienceEnabled());
    }

    @Test
    void testMapProperty_WhenKeyNotMapped_ReturnsSameKey() {
        assertEquals("someKey", conjurConfig.mapProperty("someKey"));
    }

    @Test
    void testParseDuration_InvalidFormat_ThrowsIllegalArgumentException() throws Exception {
        java.lang.reflect.Method method = ConjurConfig.class.getDeclaredMethod("parseDuration", String.class);
        method.setAccessible(true);

        try {
            method.invoke(conjurConfig, "5k");
            fail("Expected IllegalArgumentException to be thrown");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof IllegalArgumentException);
            assertTrue(cause.getMessage().contains("Unsupported duration format"));
        }
    }

    private void setMapping(String suffix, String value) {
        Map<String, String> mockMapping = new HashMap<>();
        mockMapping.put("resilience." + suffix, value);

        ConjurProperties mockProps = mock(ConjurProperties.class);
        when(mockProps.getMapping()).thenReturn(mockMapping);

        BindResult<ConjurProperties> result = mock(BindResult.class);
        when(result.get()).thenReturn(mockProps);
        when(result.isBound()).thenReturn(true);

        ConjurConfig.loadMappingProps(result);
    }


}
