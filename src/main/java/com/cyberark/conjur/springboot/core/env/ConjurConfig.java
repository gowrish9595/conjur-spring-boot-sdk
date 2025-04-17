package com.cyberark.conjur.springboot.core.env;

import static com.cyberark.conjur.springboot.constant.ConjurConstant.CONJUR_PREFIX;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ResourceUtils;

import com.cyberark.conjur.springboot.constant.ConjurConstant;
import com.cyberark.conjur.springboot.domain.ConjurProperties;

/**
 * This class loads the external configured conjur.properties file and resolves
 * the keys values defined in properties file.
 */

public class ConjurConfig implements EnvironmentAware, BeanFactoryPostProcessor {

    private static final Properties PROPS = new Properties();

    private static final Logger LOGGER = LoggerFactory.getLogger(ConjurConfig.class);
    private static Map<String, String> mapping = new HashMap<>();
    private Boolean resilienceEnabled;

    private int resilienceMaxAttempts = 0;
    private Duration resilienceWaitDuration;

    /**
     * The Environment.
     */
    private Environment environment;

    /**
     * @param name - key define at given property file.
     * @return - corresponding value of key defined at given property file.
     */
    public String mapProperty(String name) {

        String mapped = PROPS.getProperty(ConjurConstant.CONJUR_MAPPING + name);
        return mapped != null ? mapped : name;

    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

        final BindResult<ConjurProperties> result = Binder.get(environment).bind(CONJUR_PREFIX, ConjurProperties.class);
        if (result.isBound()) {
            loadMappingProps(result);
            initializeResilience(result); // set from env properties
            initializeMaxAttempts(result); // retrieve max attempts from env properties
            initializeWaitDuration(result); // retrieve wait Duration from env properties
        }

    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public static void loadMappingProps(BindResult<ConjurProperties> result) {
        String mappingPath = result.get().getMappingPath();
        InputStream propsFile = null;

        if (mappingPath != null) {
            try {
                File file = ResourceUtils.getFile(mappingPath);
                propsFile = Files.newInputStream(file.toPath());
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        } else {
            propsFile = ConjurConfig.class.getResourceAsStream(ConjurConstant.CONJUR_PROPERTIES);
        }

        if (propsFile != null) {
            try {
                PROPS.load(propsFile);

            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            } finally {
                try {
                    propsFile.close();
                } catch (IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        }
        mapping = result.get().getMapping();
        if (!CollectionUtils.isEmpty(mapping)) {
            result.get().getMapping().forEach((key, value) -> {
                PROPS.setProperty(ConjurConstant.CONJUR_MAPPING + key, value);
            });
        }

    }


    private <T> T loadResilienceProperty(
        T currentVal,
        Predicate<T> isSet,
        Consumer<T> store,
        String configKeySuffix,
        String propertyKey,
        T defaultVal,
        Function<String, T> parser,
        String logLabel
    ) {
        if (isSet.test(currentVal)) {
            return currentVal;
        }

        // Load from mapping
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (entry.getKey().toLowerCase().endsWith(configKeySuffix.toLowerCase())) {
                try {
                    T value = parser.apply(entry.getValue());
                    store.accept(value);
                    LOGGER.info("Loaded {} from mapping: {}", logLabel, value);
                    return value;
                } catch (Exception e) {
                    LOGGER.warn("Invalid {} in mapping '{}', using fallback", logLabel, entry.getValue(), e);
                }
            }
        }

        // Load from PROPS
        String propVal = PROPS.getProperty(propertyKey);
        if (propVal != null) {
            try {
                T value = parser.apply(propVal);
                store.accept(value);
                LOGGER.info("Loaded {} from PROPS: {}", logLabel, value);
                return value;
            } catch (Exception e) {
                LOGGER.warn("Invalid {} in PROPS '{}', using default", logLabel, propVal, e);
            }
        }

        // Default
        store.accept(defaultVal);
        LOGGER.info("Using default {}: {}", logLabel, defaultVal);
        return defaultVal;
    }

    public boolean getResilienceEnabled() {
        return loadResilienceProperty(
            resilienceEnabled,
            val -> val != null,
            val -> resilienceEnabled = val,
            "enabled",
            ConjurConstant.CONJUR_PREFIX + "enabled",
            true,
            Boolean::parseBoolean,
            "resilienceEnabled"
        );
    }

    public int getResilienceMaxAttempts() {
        return loadResilienceProperty(
            resilienceMaxAttempts,
            val -> val > 0,
            val -> resilienceMaxAttempts = val,
            "max-attempts",
            ConjurConstant.CONJUR_PREFIX + "max-attempts",
            3,
            Integer::parseInt,
            "maxAttempts"
        );
    }

    public Duration getResilienceWaitDuration() {
        return loadResilienceProperty(
            resilienceWaitDuration,
            Objects::nonNull,
            val -> resilienceWaitDuration = val,
            "wait-duration",
            ConjurConstant.CONJUR_PREFIX + "wait-duration",
            Duration.ofMillis(500),
            this::parseDuration,
            "waitDuration"
        );
    }

    private Duration parseDuration(String value) {
        value = value.trim().toLowerCase();

        if (value.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(value.replace("ms", "")));
        } else if (value.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(value.replace("s", "")));
        } else if (value.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(value.replace("m", "")));
        } else if (value.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(value.replace("h", "")));
        } else if (value.endsWith("d")) {
            return Duration.ofDays(Long.parseLong(value.replace("d", "")));
        } else if (value.matches("\\d+")) {
            // Default fallback: plain number is treated as seconds
            return Duration.ofSeconds(Long.parseLong(value));
        } else {
            throw new IllegalArgumentException("Unsupported duration format: " + value);
        }
    }


    private void initializeResilience(BindResult<ConjurProperties> result) {
        resilienceEnabled = result.get().getResilienceEnabled();
    }

    private void initializeMaxAttempts(BindResult<ConjurProperties> result) {
        resilienceMaxAttempts = result.get().getResilienceMaxAttempts();
    }

    private void initializeWaitDuration(BindResult<ConjurProperties> result) {
        resilienceWaitDuration = result.get().getResilienceWaitDuration();

    }


}
