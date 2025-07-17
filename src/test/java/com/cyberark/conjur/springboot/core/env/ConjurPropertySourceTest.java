package com.cyberark.conjur.springboot.core.env;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.cyberark.conjur.sdk.endpoint.SecretsApi;
import com.cyberark.conjur.sdk.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.type.AnnotationMetadata;

import java.lang.reflect.Field;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class ConjurPropertySourceTest {

    @Mock
    private SecretsApi secretsApi;
    @Mock
    private ConjurConfig conjurConfig;

    @Mock
    private AnnotationMetadata metadata;

    private ConjurPropertySource propertySource;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        propertySource = new ConjurPropertySource("testVault/");
        propertySource.setSecretsApi(secretsApi);
        propertySource.setConjurConfig(conjurConfig);
    }

    @Test
    void testSetSecretsApiAndConjurConfig() {
        assertNotNull(propertySource);
    }

    @Test
    void testGetProperty_keyNotInProperties_returnsNull() {
        Object result = propertySource.getProperty("unknown.key");
        assertNull(result);
    }

    @Test
    void testConstructorWithAnnotationMetadata_extractsValueAnnotations() throws Exception {
        when(metadata.getClassName()).thenReturn(TestBean.class.getName());
        ConjurPropertySource source = new ConjurPropertySource("testVault", "info", metadata);

        Field propsField = ConjurPropertySource.class.getDeclaredField("properties");
        propsField.setAccessible(true);
        List<String> props = (List<String>) propsField.get(source);
        assertTrue(props.contains("${sample.value}"));
    }

    @Test
    public void testMockedSecretRetrieval() throws ApiException {
        String expectedSecret = "dbuserName";
        String secretPath = "db/dbuserName";
        when(secretsApi.getSecret(any(), any(), eq(secretPath))).thenReturn(expectedSecret);

        String actualSecret = secretsApi.getSecret(null, null, secretPath);

        assertNotNull(actualSecret, "Secret should not be null");
        assertEquals(expectedSecret, actualSecret, "Secret should match expected value");

        verify(secretsApi, times(1)).getSecret(any(), any(), eq(secretPath));
    }

    @Test
    public void testMockedBulkSecretRetrieval() throws ApiException {
        String secretPath1 = "db/dbuserName";
        String secretPath2 = "db/dbpassWord";

        String expectedSecret1 = "dbuserName";
        String expectedSecret2 = "dbpassWord";

        when(secretsApi.getSecret(any(), any(), eq(secretPath1))).thenReturn(expectedSecret1);
        when(secretsApi.getSecret(any(), any(), eq(secretPath2))).thenReturn(expectedSecret2);

        String actualSecret1 = secretsApi.getSecret(null, null, secretPath1);
        String actualSecret2 = secretsApi.getSecret(null, null, secretPath2);

        assertNotNull(actualSecret1, "Secret 1 should not be null");
        assertEquals(expectedSecret1, actualSecret1, "Secret 1 should match");

        assertNotNull(actualSecret2, "Secret 2 should not be null");
        assertEquals(expectedSecret2, actualSecret2, "Secret 2 should match");

        verify(secretsApi, times(1)).getSecret(any(), any(), eq(secretPath1));
        verify(secretsApi, times(1)).getSecret(any(), any(), eq(secretPath2));
    }

    public static class TestBean {
        @Value("${sample.value}")
        private String val;
    }
}
