package com.matchforge.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
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
class FlywayBaselineAdoptionIT {

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("matchforge_adopt")
			.withUsername("matchforge")
			.withPassword("matchforge");

	@DynamicPropertySource
	static void registerDatasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("MATCHFORGE_FLYWAY_BASELINE", () -> "true");
		registry.add(
				"matchforge.security.jwt-secret",
				() -> "test-jwt-secret-key-at-least-32-characters-long");
		registry.add("matchforge.security.admin-username", () -> "itadmin");
		registry.add("matchforge.security.admin-password", () -> "ItAdminPassphrase-1");
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeAll
	static void seedLegacySchema() throws Exception {
		String v1 = new ClassPathResource("db/migration/V1__baseline.sql")
				.getContentAsString(StandardCharsets.UTF_8);
		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement()) {
			for (String fragment : v1.split(";")) {
				String sql = fragment.trim();
				if (!sql.isBlank()) {
					statement.execute(sql);
				}
			}
			UUID playerId = UUID.randomUUID();
			Instant now = Instant.parse("2026-01-01T00:00:00Z");
			statement.execute(
					"""
					INSERT INTO players (id, created_at, updated_at, username, skill_rating, status)
					VALUES ('%s', '%s', '%s', 'legacy_player', 1200, 'OFFLINE')
					"""
							.formatted(playerId, now, now));
		}
	}

	@Test
	void baselineAdoptionPreservesLegacyDataAndAppliesNewMigrations() {
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM players", Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT username FROM players", String.class)).isEqualTo("legacy_player");
		assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('public.accounts')", String.class))
				.isEqualTo("accounts");
		Integer migrations = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
		assertThat(migrations).isGreaterThanOrEqualTo(2);
	}
}