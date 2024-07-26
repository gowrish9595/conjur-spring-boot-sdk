package com.cyberark.conjur.springboot.processor;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cyberark.conjur.sdk.ApiException;
import com.cyberark.conjur.sdk.endpoint.SecretsApi;
import com.cyberark.conjur.springboot.constant.ConjurConstant;
import com.cyberark.conjur.springboot.core.env.ConjurConnectionManager;

public class ConjurRetrieveSecretService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConjurRetrieveSecretService.class);

	private final SecretsApi secretsApi;

	public ConjurRetrieveSecretService(SecretsApi secretsApi) {
		this.secretsApi = secretsApi;
	}

	/**
	 * This method retrieves multiple secrets for custom annotation's keys.
	 *
	 * @param keys - query to vault.
	 * @return secrets - output from the vault.
	 */
	public byte[] retriveMultipleSecretsForCustomAnnotation(String[] keys) throws ApiException {

		Object result = null;
		StringBuilder kind = new StringBuilder();
		if (keys.length > 0) {
			kind.append("" + ConjurConstant.CONJUR_ACCOUNT + ":variable:" + keys[0] + "");
			for (int i = 1; i < keys.length; i++) {
				kind.append("," + ConjurConstant.CONJUR_ACCOUNT + ":variable:" + keys[i]);
			}
		}
		try {
			result = secretsApi.getSecrets(new String(kind));
		} catch (ApiException ae) {
			throw new ApiException(ae.getCode(), ae.getMessage(), null, ae.getResponseBody());

		}
		return processMultipleSecretResult(result);

	}

	/**
	 * This method retrieves single secret for custom annotation's key value.
	 * 
	 * @param key - query to vault.
	 * @return secrets - output from the vault.
	 */
	public byte[] retriveSingleSecretForCustomAnnotation(String key) throws ApiException {
		byte[] result = null;
		try {
			String account = ConjurConnectionManager.getAccount(secretsApi);
			String secret = secretsApi.getSecret(account, ConjurConstant.CONJUR_KIND, key);
			result = secret != null ? secret.getBytes() : null;
		} catch (ApiException ae) {
			throw new ApiException(ae.getCode(), ae.getResponseBody());
		}
		return result;
	}

	private byte[] processMultipleSecretResult(Object result) {
	    String key = "";
	    String value = "";
	    String[] parts = result.toString().split(", ");
        StringBuilder sb = new StringBuilder();

        for(int i=0; i<parts.length;i++) {
        	if (parts[i].endsWith("}"))
            parts[i] = parts[i].substring(0, parts[i].length() - 1); 
	    	String[] tmpArr = parts[i].split("=");
	        key= tmpArr[0];
	        String[] tmp2 = key.split("/");
	        key = tmp2[tmp2.length-1];
	        value = parts[i].substring(parts[i].indexOf("=")+1, parts[i].length());
	        sb.append(key+"="+value);
	        if (i < parts.length - 1) {
	        sb.append(",");
	    }   
        }
	    return sb.toString().getBytes();
	}
}
