package com.cyberark.conjur.springboot.service;


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.cyberark.conjur.sdk.ApiException;
import com.cyberark.conjur.sdk.endpoint.SecretsApi;
import com.cyberark.conjur.springboot.constant.ConjurConstant;
import com.cyberark.conjur.springboot.core.env.ConjurConfig;
import com.cyberark.conjur.springboot.core.env.ConjurConnectionManager;
import com.cyberark.conjur.springboot.util.ConjurSecretUtils;
import com.google.gson.Gson;

/**
 * Custom property source chain to fetch secrets from Conjur at app startup.
 */
public class CustomPropertySourceChain extends PropertyProcessorChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomPropertySourceChain.class);

    private PropertyProcessorChain chain;
    private SecretsApi secretsApi;
    
    @Autowired
    private ConjurConfig conjurConfig;
    
    private ConjurSecretUtils conjurSecretUtils;
    
    public CustomPropertySourceChain(String name) {

        super("customPropertySource");

    }

    @Override
    public void setNextChain(PropertyProcessorChain nextChain) {
        this.chain = nextChain;
    }

    public void setSecretsApi(SecretsApi secretsApi) {
        this.secretsApi = secretsApi;
    }

    public void setConjurConfig(ConjurConfig conjurConfig) {
        this.conjurConfig = conjurConfig;
    }

    @Override
    public String[] getPropertyNames() {
        return new String[0];
    }

    @Override
    public Object getProperty(String key) {

        if (skipConstantKey(key)) {
            return null;
        }
        
        boolean resilienceEnabled = false;
        int maxAttempt =0;
		Duration waitDuration;

        resilienceEnabled = conjurConfig.getResilienceEnabled();
        maxAttempt = conjurConfig.getResilienceMaxAttempts();
		waitDuration = conjurConfig.getResilienceWaitDuration();

    	conjurSecretUtils = ConjurSecretUtils.create(resilienceEnabled,maxAttempt,waitDuration);

        String account = ConjurConnectionManager.getAccount(secretsApi);

        Gson gson = new Gson();

        key = conjurConfig.mapProperty(key);

        try {
            if (key.contains(",")) {
                // Bulk secret retrieval
                String[] keys = key.split(",");
                List<String> fullKeys = new ArrayList<>();
                for (String rawKey : keys) {
                    String mappedKey = conjurConfig.mapProperty(rawKey.trim());
                    fullKeys.add(account + ":variable:" + mappedKey);
                }

                return conjurSecretUtils.fetchSecrets(secretsApi, String.join(",", fullKeys), gson);
            } else {
                // Single secret fetch
                String mappedKey = conjurConfig.mapProperty(key);
                return conjurSecretUtils.fetchSecret(secretsApi, account, ConjurConstant.CONJUR_KIND, mappedKey);
            }
        } catch (ApiException e) {
            LOGGER.error("Error fetching secrets from Conjur", e);
            throw new IllegalStateException("Failed to fetch secret(s) for key: " + key, e);
        }
    }

    private boolean skipConstantKey(String key) {
        return key.startsWith(ConjurConstant.SPRING_VAR)
            || key.startsWith(ConjurConstant.SERVER_VAR)
            || key.startsWith(ConjurConstant.ERROR)
            || key.startsWith(ConjurConstant.SPRING_UTIL)
            || key.startsWith(ConjurConstant.CONJUR_PREFIX)
            || key.startsWith(ConjurConstant.ACTUATOR_PREFIX)
            || key.startsWith(ConjurConstant.LOGGING_PREFIX)
            || key.startsWith(ConjurConstant.KUBERNETES_PREFIX)
            || key.contains(ConjurConstant.RESILIENCE_PREFIX);
    }
}