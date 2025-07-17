package com.cyberark.conjur.springboot.core.env;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import com.cyberark.conjur.sdk.endpoint.SecretsApi;
import com.cyberark.conjur.springboot.constant.ConjurConstant;
import com.cyberark.conjur.springboot.util.ConjurSecretUtils;
import com.google.gson.Gson;

/**
 * 
 * This class resolves the secret value for given vault path at application load
 * time from the Conjur vault.
 *
 */
public class ConjurPropertySource extends EnumerablePropertySource<Object> {

	private String vaultInfo = "";

	private String vaultPath = "";

	private SecretsApi secretsApi;

	private List<String> properties;

	private ConjurConfig conjurConfig;

	private ConjurSecretUtils conjurSecretUtils;
	private boolean resilienceEnabled = false;

	private static final Logger LOGGER = LoggerFactory.getLogger(ConjurPropertySource.class);

	public ConjurPropertySource(String vaultPath) {
		super(vaultPath + "@");
		this.vaultPath = vaultPath;
	}

	public ConjurPropertySource(String vaultPath, String vaultInfo, AnnotationMetadata importingClassMetadata)
			throws ClassNotFoundException {
		super(vaultPath + "@" + vaultInfo);
		this.vaultPath = vaultPath;
		this.vaultInfo = vaultInfo;

		List<String> properties = new ArrayList<>();
		Class<?> annotatedClass = ClassUtils.forName((importingClassMetadata).getClassName(),
				getClass().getClassLoader());
		for (Field field : annotatedClass.getDeclaredFields()) {
			if (field.isAnnotationPresent(Value.class)) {
				String value = field.getAnnotation(Value.class).value();
				properties.add(value);
			}
		}
		this.properties = properties;
	}

	@Override
	public String[] getPropertyNames() {
		return new String[0];
	}

	/**
	 * Method which resolves @Value annotation queries and return result in the form
	 * of byte array or String.
	 */
	@Override
	public Object getProperty(String key) {

		Gson gson = new Gson();
		Object secretValue = null;

		if (!vaultPath.endsWith("/")) {
			this.vaultPath = vaultPath.concat("/");
		}

		if (propertyExists(key)) {
			int maxAttempt =0;
			Duration waitDuration;

			key = conjurConfig.mapProperty(key);
			resilienceEnabled = conjurConfig.getResilienceEnabled();
			maxAttempt = conjurConfig.getResilienceMaxAttempts();
			waitDuration = conjurConfig.getResilienceWaitDuration();

			conjurSecretUtils = ConjurSecretUtils.create(resilienceEnabled,maxAttempt,waitDuration);

			try {
				String account = ConjurConnectionManager.getAccount(secretsApi);

				if (key.contains(",")) {
					// Bulk fetch multiple keys
					String[] keys = key.split(",");
					StringBuilder fullKeysBuilder = new StringBuilder();

					if (keys.length > 0) {
						String firstMappedKey = conjurConfig.mapProperty(keys[0]);
						fullKeysBuilder.append(account).append(":variable:").append(vaultPath).append(firstMappedKey);
						for (int i = 1; i < keys.length; i++) {
							String mappedKey = conjurConfig.mapProperty(keys[i]);
							fullKeysBuilder.append(",").append(account).append(":variable:").append(vaultPath)
									.append(mappedKey);
						}
					}

					secretValue = conjurSecretUtils.fetchSecrets(secretsApi, fullKeysBuilder.toString(), gson);

				} else {
					LOGGER.info("Calling single secret retrieval >>");
					String fullPath = vaultPath + key;
					byte[] secret = conjurSecretUtils.fetchSecret(secretsApi, account, ConjurConstant.CONJUR_KIND,
							fullPath);
					secretValue = secret;
				}
			} catch (Exception e) {
				LOGGER.error("Error fetching secret(s) from Conjur", e);
				return null;
			}
		}

		return secretValue;
	}

	/**
	 * To set the secret api value
	 * 
	 * @param secretsApi
	 */
	public void setSecretsApi(SecretsApi secretsApi) {
		this.secretsApi = secretsApi;
	}

	private boolean propertyExists(String key) {
		return properties != null && properties.stream().anyMatch(property -> property.contains(key));
	}

	public void setConjurConfig(ConjurConfig conjurConfig) {
		this.conjurConfig = conjurConfig;
	}

}