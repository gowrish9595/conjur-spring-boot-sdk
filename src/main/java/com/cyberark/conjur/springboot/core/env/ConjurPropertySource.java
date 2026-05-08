package com.cyberark.conjur.springboot.core.env;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
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
 * Resolves secret values for a given vault path at application load time from
 * the Conjur vault.
 *
 * <p>Two modes are supported:
 * <ul>
 *   <li><b>Annotation mode</b> (legacy): created by {@code Registrar} from a
 *       {@code @ConjurPropertySource} annotation. The source only answers for
 *       keys that appear as {@code @Value("${...}")} fields on the annotated
 *       class, and returns {@code byte[]}.</li>
 *   <li><b>Annotation-free mode</b>: created by
 *       {@code ConjurEnvironmentPostProcessor} or the {@code ConfigData} API.
 *       Answers any unresolved key (after framework-prefix filtering) and
 *       returns a decoded UTF-8 {@code String} so {@code @Value} can bind to
 *       {@code String} fields directly.</li>
 * </ul>
 */
public class ConjurPropertySource extends EnumerablePropertySource<Object> {

	private String vaultInfo = "";

	private String vaultPath = "";

	private SecretsApi secretsApi;

	private List<String> properties;

	private ConjurConfig conjurConfig;

	private ConjurSecretUtils conjurSecretUtils;
	private boolean resilienceEnabled = false;

	private final boolean annotationFree;

	private static final Logger LOGGER = LoggerFactory.getLogger(ConjurPropertySource.class);

	public ConjurPropertySource(String vaultPath) {
		super(vaultPath + "@");
		this.vaultPath = vaultPath;
		this.annotationFree = false;
	}

	public ConjurPropertySource(String vaultPath, String vaultInfo, AnnotationMetadata importingClassMetadata)
			throws ClassNotFoundException {
		super(vaultPath + "@" + vaultInfo);
		this.vaultPath = vaultPath;
		this.vaultInfo = vaultInfo;
		this.annotationFree = false;

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

	/**
	 * Annotation-free constructor used by the {@code EnvironmentPostProcessor}
	 * and {@code ConfigData} integrations. The source answers for any
	 * non-framework key, returning a decoded {@code String} value.
	 */
	public ConjurPropertySource(String name, String vaultPath, SecretsApi secretsApi, ConjurConfig conjurConfig) {
		super(name);
		this.vaultPath = vaultPath == null ? "" : vaultPath;
		this.secretsApi = secretsApi;
		this.conjurConfig = conjurConfig;
		this.annotationFree = true;
	}

	@Override
	public String[] getPropertyNames() {
		return new String[0];
	}

	@Override
	public Object getProperty(String key) {
		if (annotationFree) {
			return resolveAnnotationFree(key);
		}
		return resolveLegacy(key);
	}

	private Object resolveAnnotationFree(String key) {
		if (key == null || isFrameworkKey(key)) {
			return null;
		}
		ensureSecretUtils();
		try {
			String account = ConjurConnectionManager.getAccount(secretsApi);
			String mappedKey = conjurConfig.mapProperty(key);
			String fullPath = normalizedVaultPath() + mappedKey;
			byte[] secret = conjurSecretUtils.fetchSecret(secretsApi, account, ConjurConstant.CONJUR_KIND, fullPath);
			if (secret == null) {
				return null;
			}
			return new String(secret, StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.debug("Conjur lookup failed for key '{}': {}", key, e.getMessage());
			return null;
		}
	}

	private Object resolveLegacy(String key) {
		Gson gson = new Gson();
		Object secretValue = null;

		if (!vaultPath.endsWith("/")) {
			this.vaultPath = vaultPath.concat("/");
		}

		if (propertyExists(key)) {
			ensureSecretUtils();

			try {
				String account = ConjurConnectionManager.getAccount(secretsApi);
				key = conjurConfig.mapProperty(key);

				if (key.contains(",")) {
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

	private void ensureSecretUtils() {
		int maxAttempt = conjurConfig.getResilienceMaxAttempts();
		Duration waitDuration = conjurConfig.getResilienceWaitDuration();
		resilienceEnabled = conjurConfig.getResilienceEnabled();
		conjurSecretUtils = ConjurSecretUtils.create(resilienceEnabled, maxAttempt, waitDuration);
	}

	private String normalizedVaultPath() {
		if (vaultPath == null || vaultPath.isEmpty()) {
			return "";
		}
		return vaultPath.endsWith("/") ? vaultPath : vaultPath + "/";
	}

	/**
	 * Mirrors {@code CustomPropertySourceChain.skipConstantKey}. Used so the
	 * annotation-free source does not hit Conjur for framework keys.
	 */
	public static boolean isFrameworkKey(String key) {
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
