package com.matchforge.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = false)
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIT {

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("matchforge_it")
			.withUsername("matchforge")
			.withPassword("matchforge");

	@DynamicPropertySource
	static void registerDatasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("MATCHFORGE_FLYWAY_BASELINE", () -> "false");
		registry.add(
				"matchforge.security.jwt-secret",
				() -> "test-jwt-secret-key-at-least-32-characters-long");
		registry.add("matchforge.security.admin-username", () -> "itadmin");
		registry.add("matchforge.security.admin-password", () -> "ItAdminPassphrase-1");
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void freshDatabaseAppliesMigrations() {
		Integer migrations = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
		assertThat(migrations).isGreaterThanOrEqualTo(3);
		assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('public.accounts')", String.class))
				.isEqualTo("accounts");
		assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('public.players')", String.class))
				.isEqualTo("players");
	}
}