package com.matchforge.common.config;

import java.util.Arrays;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * Ensures the {@code prod} profile always receives explicit database credentials
 * from the environment (for example Render secrets), never implicit localhost defaults.
 */
public class ProductionDatabaseEnvironmentValidator
		implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

	private static final String PROD_PROFILE = "prod";

	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
		validate(event.getEnvironment());
	}

	static void validate(ConfigurableEnvironment environment) {
		if (!Arrays.asList(environment.getActiveProfiles()).contains(PROD_PROFILE)) {
			return;
		}
		requireEnv(environment, "DB_URL");
		requireEnv(environment, "DB_USERNAME");
		requireEnv(environment, "DB_PASSWORD");

		String dbUrl = environment.getProperty("DB_URL");
		if (dbUrl.contains("localhost") || dbUrl.contains("127.0.0.1")) {
			throw new IllegalStateException(
					"DB_URL must target the managed PostgreSQL instance in production (localhost is for local development only).");
		}
	}

	private static void requireEnv(ConfigurableEnvironment environment, String name) {
		String value = environment.getProperty(name);
		if (!StringUtils.hasText(value)) {
			throw new IllegalStateException("Missing required production environment variable: " + name);
		}
	}
}