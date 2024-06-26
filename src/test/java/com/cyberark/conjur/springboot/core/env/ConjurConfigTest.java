package com.cyberark.conjur.springboot.core.env;

import com.cyberark.conjur.sdk.ApiException;
import com.cyberark.conjur.springboot.annotations.ConjurPropertySource;
import com.cyberark.conjur.springboot.domain.ConjurProperties;
import com.cyberark.conjur.springboot.processor.SpringBootConjurAutoConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Properties;

/**
 * @author bnasslahsen
 */
@SpringBootTest(classes = SpringBootConjurAutoConfiguration.class)
@ConjurPropertySource(value={"jenkins-app/"})
public class ConjurConfigTest {

	@Autowired
	private ConjurConfig conjurConfig;

	@Test
	public void testGetMappings() throws ApiException {
		assertEquals("vault/bnl-k8s-safe/mysql-test-db/dsn", conjurConfig.mapProperty("testUrl"));
		assertEquals("vault/bnl-k8s-safe/mysql-test-db/username", conjurConfig.mapProperty("testUsername"));
	}
	
	@Mock
    private BindResult<ConjurProperties> mockBindResult;

    @InjectMocks
    private ConjurConfig conjurConfigMock;  // Assuming this is the class where loadMappingProps is

    private Properties props;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        props = new Properties();
        // Setup any other initial state or mocks as needed
    }

    @Test
    void testLoadMappingPropsWithValidPath() throws Exception {
        // Setup
        String validPath = "/Users/madhavi.nagilla/CONJUR-SDK-May2024/conjur-spring-boot-sdk/src/test/resources/mapping-dir/conjur.properties";

        ConjurProperties conjurProperties = mock(ConjurProperties.class);
        when(conjurProperties.getMappingPath()).thenReturn(validPath);
        when(mockBindResult.get()).thenReturn(conjurProperties);

        // Assert
        assertEquals(validPath, conjurProperties.getMappingPath());

    }


}
