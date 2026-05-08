package com.cyberark.conjur.springboot.config;

import java.io.IOException;
import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;

import com.cyberark.conjur.sdk.endpoint.SecretsApi;
import com.cyberark.conjur.springboot.core.env.ConjurConfig;
import com.cyberark.conjur.springboot.core.env.ConjurConnectionManager;
import com.cyberark.conjur.springboot.core.env.ConjurPropertySource;
import com.cyberark.conjur.springboot.domain.ConjurProperties;

/**
 * Loads a {@link ConfigData} containing a {@link ConjurPropertySource} for a
 * resolved {@code conjur://<path>} import.
 *
 * <p>Bootstraps the Conjur API client outside the bean lifecycle by reading
 * {@link ConjurProperties} from the bootstrap context (registered by
 * {@link ConjurConfigDataLocationResolver}), calling
 * {@link ConjurConnectionManager#bootstrapConnection(ConjurProperties)}, then
 * handing the resulting {@link SecretsApi} to a property source scoped to the
 * imported vault path.
 */
public class ConjurConfigDataLoader implements ConfigDataLoader<ConjurConfigDataResource> {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConjurConfigDataLoader.class);

	@Override
	public ConfigData load(ConfigDataLoaderContext context, ConjurConfigDataResource resource)
			throws IOException, ConfigDataResourceNotFoundException {

		ConjurProperties properties = context.getBootstrapContext().get(ConjurProperties.class);

		if (properties == null || StringUtils.isEmpty(properties.getApplianceUrl())) {
			if (resource.isOptional()) {
				LOGGER.info("Skipping optional Conjur import {}: conjur.appliance-url not configured", resource);
				return new ConfigData(Collections.emptyList());
			}
			throw new ConfigDataResourceNotFoundException(resource,
					new IllegalStateException("conjur.appliance-url must be set to import " + resource));
		}

		try {
			ConjurConnectionManager.bootstrapConnection(properties);
			SecretsApi secretsApi = new SecretsApi();
			ConjurConfig conjurConfig = ConjurConfig.fromProperties(properties);

			String name = "conjurConfigData[" + resource.getVaultPath() + "]";
			ConjurPropertySource source = new ConjurPropertySource(name, resource.getVaultPath(), secretsApi,
					conjurConfig);

			LOGGER.info("Loaded Conjur ConfigData for vault path '{}'", resource.getVaultPath());
			return new ConfigData(Collections.singletonList(source));
		} catch (Exception e) {
			if (resource.isOptional()) {
				LOGGER.warn("Failed to load optional Conjur import {}: {}", resource, e.getMessage());
				return new ConfigData(Collections.emptyList());
			}
			throw new ConfigDataResourceNotFoundException(resource, e);
		}
	}
}
