package com.cyberark.conjur.springboot.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TelemetryTest {

    @Test
    public void testTelemetryHeaders() {
    	ConjurProperties properties = new ConjurProperties();

        String integrationName = properties.getIntegrationName();
        String integrationType = properties.getIntegrationType();
        String integrationVersion = properties.getIntegrationVersion();
        String vendorName = properties.getVendorName();

        assertEquals("SecretsManagerSpringBoot SDK", integrationName);
        assertEquals("cybr-secretsmanager-springboot", integrationType);
        assertNotNull(integrationVersion);
        assertEquals("CyberArk", vendorName);
    }
}

