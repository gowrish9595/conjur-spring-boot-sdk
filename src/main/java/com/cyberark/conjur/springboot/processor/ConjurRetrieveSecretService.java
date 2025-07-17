package com.cyberark.conjur.springboot.processor;


import java.time.Duration;

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


public class ConjurRetrieveSecretService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConjurRetrieveSecretService.class);

    private final SecretsApi secretsApi;

    private ConjurSecretUtils conjurSecretUtils;
    
    @Autowired
    private ConjurConfig conjurConfig;

    public ConjurRetrieveSecretService(SecretsApi secretsApi) {
        this.secretsApi = secretsApi;
    }

    private void initializeConjurSecretUtils() {
    	boolean resilienceEnabled = false;
    	int maxAttempt =0;
		Duration waitDuration;

    	resilienceEnabled = conjurConfig.getResilienceEnabled();
    	maxAttempt = conjurConfig.getResilienceMaxAttempts();
		waitDuration = conjurConfig.getResilienceWaitDuration();

    	conjurSecretUtils = ConjurSecretUtils.create(resilienceEnabled,maxAttempt,waitDuration);

    }

    /**
     * This method retrieves multiple secrets for custom annotation's keys.
     *
     * @param keys - query to vault.
     * @return secrets - output from the vault.
     * @throws ApiException if error fetching secrets
     */
    public byte[] retriveMultipleSecretsForCustomAnnotation(String[] keys) throws ApiException {
        if (conjurSecretUtils == null) {
            initializeConjurSecretUtils();
        }

        if (keys == null || keys.length == 0) {
            return new byte[0];
        }

        StringBuilder fullKeysBuilder = new StringBuilder();
        String account = ConjurConnectionManager.getAccount(secretsApi);

        fullKeysBuilder.append(account).append(":variable:").append(keys[0]);
        for (int i = 1; i < keys.length; i++) {
            fullKeysBuilder.append(",").append(account).append(":variable:").append(keys[i]);
        }

        Gson gson = new Gson();
        try {
            Object result = conjurSecretUtils.fetchSecrets(secretsApi, fullKeysBuilder.toString(), gson);

            if (result instanceof String) {
                return ((String) result).getBytes();
            } else if (result instanceof byte[]) {
                return (byte[]) result;
            } else {
                return result.toString().getBytes();
            }
        } catch (ApiException e) {
            LOGGER.error("Error during bulk secret retrieval for keys: {}", fullKeysBuilder, e);
            throw e;
        }
    }

    /**
     * This method retrieves single secret for custom annotation's key value.
     *
     * @param key - query to vault.
     * @return secrets - output from the vault.
     * @throws ApiException if error fetching secret
     */
    public byte[] retriveSingleSecretForCustomAnnotation(String key) throws ApiException {
        if (conjurSecretUtils == null) {
            initializeConjurSecretUtils();
        }

        try {
            String account = ConjurConnectionManager.getAccount(secretsApi);
            byte[] secret = conjurSecretUtils.fetchSecret(secretsApi, account, ConjurConstant.CONJUR_KIND, key);

            if (secret == null) {
                throw new ApiException(404, "Secret not found for key: " + key);
            }

            return secret;
        } catch (ApiException e) {
            LOGGER.error("Error fetching secret for key: {}", key, e);
            throw e;
        } catch (Exception e) {
            LOGGER.error("Unexpected error fetching secret for key: {}", key, e);
            throw new ApiException(500, "Unexpected error while fetching secret");
        }
    }
}
