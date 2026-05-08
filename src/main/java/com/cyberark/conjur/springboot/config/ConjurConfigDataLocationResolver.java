package com.cyberark.conjur.springboot.config;

import static com.cyberark.conjur.springboot.constant.ConjurConstant.CONJUR_PREFIX;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationNotFoundException;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.context.properties.bind.BindResult;

import com.cyberark.conjur.springboot.domain.ConjurProperties;

/**
 * Resolves {@code spring.config.import: conjur://<path>} entries to a
 * {@link ConjurConfigDataResource}. Mirrors the
 * {@code VaultConfigDataLocationResolver} /
 * {@code SecretsManagerConfigDataLocationResolver} patterns used by Spring
 * Cloud Vault and Spring Cloud AWS.
 *
 * <p>Also seeds the bootstrap context with the bound
 * {@link ConjurProperties} so {@link ConjurConfigDataLoader} can read it
 * without re-binding.
 */
public class ConjurConfigDataLocationResolver
		implements ConfigDataLocationResolver<ConjurConfigDataResource> {

	private static final String PREFIX = "conjur://";

	@Override
	public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
		return location.hasPrefix(PREFIX);
	}

	@Override
	public List<ConjurConfigDataResource> resolve(ConfigDataLocationResolverContext context,
			ConfigDataLocation location)
			throws ConfigDataLocationNotFoundException, ConfigDataResourceNotFoundException {
		registerProperties(context);
		String vaultPath = location.getNonPrefixedValue(PREFIX);
		return Collections.singletonList(new ConjurConfigDataResource(vaultPath, location.isOptional()));
	}

	private void registerProperties(ConfigDataLocationResolverContext context) {
		context.getBootstrapContext().registerIfAbsent(ConjurProperties.class, ignored -> {
			BindResult<ConjurProperties> result = context.getBinder().bind(CONJUR_PREFIX, ConjurProperties.class);
			return result.isBound() ? result.get() : new ConjurProperties();
		});
	}
}
