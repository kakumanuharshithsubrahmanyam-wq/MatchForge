package com.matchforge.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class ProductionServerConfigurationTest {

	@Test
	void prodServerBindsAllInterfacesAndRenderPort() throws IOException {
		MockEnvironment environment = loadProdConfig();
		environment.setProperty("PORT", "10000");
		assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
		assertThat(environment.getProperty("server.port")).isEqualTo("10000");
	}

	@Test
	void prodServerPortDefaultsTo10000WhenPortUnset() throws IOException {
		MockEnvironment environment = loadProdConfig();
		assertThat(environment.getProperty("server.port")).isEqualTo("10000");
	}

	@Test
	void prodDoesNotExposeSeparateManagementPort() throws IOException {
		MockEnvironment environment = loadProdConfig();
		assertThat(environment.getProperty("management.server.port")).isNull();
	}

	private static MockEnvironment loadProdConfig() throws IOException {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
		List<PropertySource<?>> base =
				loader.load("application.yml", new ClassPathResource("application.yml"));
		List<PropertySource<?>> prod =
				loader.load("application-prod.yml", new ClassPathResource("application-prod.yml"));
		for (PropertySource<?> source : base) {
			environment.getPropertySources().addLast(source);
		}
		for (PropertySource<?> source : prod) {
			environment.getPropertySources().addLast(source);
		}
		return environment;
	}
}