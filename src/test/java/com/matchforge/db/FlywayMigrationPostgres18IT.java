package com.matchforge.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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

/**
 * Verifies Flyway migrations and Hibernate validate on PostgreSQL 18 (Neon production major version).
 */
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = false)
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationPostgres18IT {

	private static final List<String> EXPECTED_TABLES =
			List.of("players", "queue_entries", "game_matches", "game_sessions", "session_participants", "accounts");

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
			.withDatabaseName("matchforge_pg18")
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
	void postgres18FreshDatabaseAppliesMigrationsAndValidatesSchema() {
		String version =
				jdbcTemplate.queryForObject("SELECT split_part(version(), ' ', 2)", String.class);
		assertThat(version).startsWith("18.");

		Integer migrations = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
		assertThat(migrations).isGreaterThanOrEqualTo(3);

		for (String table : EXPECTED_TABLES) {
			assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('public." + table + "')", String.class))
					.isEqualTo(table);
		}

		assertThat(
						jdbcTemplate.queryForObject(
								"""
								SELECT COUNT(*) FROM pg_indexes
								WHERE schemaname = 'public' AND indexname = 'ux_queue_entries_active_player'
								""",
								Integer.class))
				.isEqualTo(1);

		assertThat(
						jdbcTemplate.queryForObject(
								"""
								SELECT COUNT(*) FROM information_schema.table_constraints
								WHERE table_schema = 'public' AND table_name = 'players' AND constraint_type = 'UNIQUE'
								""",
								Integer.class))
				.isGreaterThanOrEqualTo(1);
	}
}