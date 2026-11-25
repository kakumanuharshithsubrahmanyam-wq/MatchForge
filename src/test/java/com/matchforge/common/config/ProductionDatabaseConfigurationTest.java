package com.matchforge.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class ProductionDatabaseConfigurationTest {

	private static final String EXTERNAL_DB_URL =
			"jdbc:postgresql://example-neon.test/neondb?sslmode=require";

	@Test
	void prodFailsWhenDbUrlMissing() {
		MockEnvironment environment = prodEnvironment();
		assertThatThrownBy(() -> ProductionDatabaseEnvironmentValidator.validate(environment))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("DB_URL");
	}

	@Test
	void prodFailsWhenDbUsernameMissing() {
		MockEnvironment environment = prodEnvironment();
		environment.setProperty("DB_URL", EXTERNAL_DB_URL);
		assertThatThrownBy(() -> ProductionDatabaseEnvironmentValidator.validate(environment))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("DB_USERNAME");
	}

	@Test
	void prodFailsWhenDbPasswordMissing() {
		MockEnvironment environment = prodEnvironment();
		environment.setProperty("DB_URL", EXTERNAL_DB_URL);
		environment.setProperty("DB_USERNAME", "matchforge_app");
		assertThatThrownBy(() -> ProductionDatabaseEnvironmentValidator.validate(environment))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("DB_PASSWORD");
	}

	@Test
	void prodRejectsLocalhostDatabaseUrl() {
		MockEnvironment environment = prodEnvironment();
		environment.setProperty("DB_URL", "jdbc:postgresql://localhost:5433/matchforge");
		environment.setProperty("DB_USERNAME", "matchforge_app");
		environment.setProperty("DB_PASSWORD", "secret");
		assertThatThrownBy(() -> ProductionDatabaseEnvironmentValidator.validate(environment))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("localhost");
	}

	@Test
	void prodUsesConfiguredExternalDatabaseUrl() throws IOException {
		MockEnvironment environment = loadConfig("prod");
		environment.setProperty("DB_URL", EXTERNAL_DB_URL);
		environment.setProperty("DB_USERNAME", "matchforge_app");
		environment.setProperty("DB_PASSWORD", "unit-test-placeholder");
		assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(EXTERNAL_DB_URL);
		assertThat(environment.getProperty("spring.datasource.url")).doesNotContain("localhost");
	}

	@Test
	void prodFlywayDoesNotDefineSeparateDataSource() throws IOException {
		MockEnvironment environment = loadConfig("prod");
		environment.setProperty("DB_URL", EXTERNAL_DB_URL);
		environment.setProperty("DB_USERNAME", "matchforge_app");
		environment.setProperty("DB_PASSWORD", "unit-test-placeholder");
		environment.setProperty("MATCHFORGE_FLYWAY_BASELINE", "false");
		assertThat(environment.getProperty("spring.flyway.url")).isNull();
		assertThat(environment.getProperty("spring.flyway.user")).isNull();
		assertThat(environment.getProperty("spring.flyway.password")).isNull();
		assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("true");
		assertThat(environment.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("false");
	}

	@Test
	void developmentDefaultsUseLocalPostgres() throws IOException {
		Environment environment = loadConfig();
		assertThat(environment.getProperty("spring.datasource.url")).contains("localhost:5433");
		assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("matchforge");
	}

	private static MockEnvironment prodEnvironment() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		return environment;
	}

	private static MockEnvironment loadConfig(String... profiles) throws IOException {
		MockEnvironment environment = new MockEnvironment();
		if (profiles.length > 0) {
			environment.setActiveProfiles(profiles);
		}
		YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
		appendYaml(loader, environment, "application.yml");
		if (Arrays.asList(profiles).contains("prod")) {
			appendYaml(loader, environment, "application-prod.yml");
		}
		return environment;
	}

	private static void appendYaml(
			YamlPropertySourceLoader loader, MockEnvironment environment, String resource) throws IOException {
		ClassPathResource classPathResource = new ClassPathResource(resource);
		List<PropertySource<?>> sources = loader.load(resource, classPathResource);
		for (PropertySource<?> source : sources) {
			environment.getPropertySources().addLast(source);
		}
	}
}