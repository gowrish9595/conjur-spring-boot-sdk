package com.cyberark.conjur.springboot.bootstrap;

import static com.cyberark.conjur.springboot.constant.ConjurConstant.CONJUR_PREFIX;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;

import com.cyberark.conjur.sdk.endpoint.SecretsApi;
import com.cyberark.conjur.springboot.core.env.ConjurConfig;
import com.cyberark.conjur.springboot.core.env.ConjurConnectionManager;
import com.cyberark.conjur.springboot.core.env.ConjurPropertySource;
import com.cyberark.conjur.springboot.domain.ConjurProperties;

/**
 * Registers a {@link ConjurPropertySource} with the Spring {@link
 * ConfigurableEnvironment} before bean instantiation, giving zero-code-change
 * Conjur lookups for any {@code @Value("${...}")} expression.
 *
 * <p>Activation: fires only when {@code conjur.appliance-url} is bound. This
 * acts as the natural feature flag — apps that pull this JAR transitively but
 * do not configure Conjur are unaffected.
 *
 * <p>For users who prefer explicit, per-import scoping (matching the
 * Spring Cloud Vault / AWS Secrets Manager experience), use the
 * {@code ConfigData} API instead via
 * {@code spring.config.import: conjur://policy/my-application}.
 */
public class ConjurEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConjurEnvironmentPostProcessor.class);

	private static final String SOURCE_NAME = "conjurEnvironmentPropertySource";

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		BindResult<ConjurProperties> result = Binder.get(environment).bind(CONJUR_PREFIX, ConjurProperties.class);
		if (!result.isBound()) {
			LOGGER.debug("Skipping ConjurEnvironmentPostProcessor: no conjur.* properties bound");
			return;
		}

		ConjurProperties props = result.get();
		if (StringUtils.isEmpty(props.getApplianceUrl())) {
			LOGGER.debug("Skipping ConjurEnvironmentPostProcessor: conjur.appliance-url is not configured");
			return;
		}

		MutablePropertySources sources = environment.getPropertySources();
		if (sources.contains(SOURCE_NAME)) {
			return;
		}

		try {
			ConjurConnectionManager.bootstrapConnection(props);
			SecretsApi secretsApi = new SecretsApi();
			ConjurConfig conjurConfig = ConjurConfig.fromBindResult(result);

			ConjurPropertySource source = new ConjurPropertySource(SOURCE_NAME, "", secretsApi, conjurConfig);
			sources.addLast(source);
			LOGGER.info("Registered Conjur property source via EnvironmentPostProcessor (appliance-url={})",
					props.getApplianceUrl());
		} catch (Exception e) {
			LOGGER.error("Failed to register Conjur property source via EnvironmentPostProcessor: {}",
					e.getMessage(), e);
		}
	}
}
