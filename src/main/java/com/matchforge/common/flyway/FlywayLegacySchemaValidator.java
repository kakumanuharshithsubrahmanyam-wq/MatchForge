package com.matchforge.common.flyway;

import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class FlywayLegacySchemaValidator {

	private static final Logger log = LoggerFactory.getLogger(FlywayLegacySchemaValidator.class);

	private static final List<String> REQUIRED_LEGACY_TABLES = List.of(
			"players",
			"queue_entries",
			"game_matches",
			"game_sessions",
			"session_participants");

	@Bean
	FlywayConfigurationCustomizer legacySchemaCustomizer(
			DataSource dataSource, @Value("${spring.flyway.baseline-on-migrate:false}") boolean baselineOnMigrate) {
		return configuration -> configuration.callbacks(new Callback() {
			@Override
			public boolean supports(Event event, Context context) {
				return baselineOnMigrate && event == Event.BEFORE_MIGRATE;
			}

			@Override
			public void handle(Event event, Context context) {
				validateLegacySchemaBeforeBaseline(new JdbcTemplate(dataSource));
			}

			@Override
			public boolean canHandleInTransaction(Event event, Context context) {
				return true;
			}

			@Override
			public String getCallbackName() {
				return "FlywayLegacySchemaValidator";
			}
		});
	}

	private void validateLegacySchemaBeforeBaseline(JdbcTemplate jdbcTemplate) {
		Integer historyCount = jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*) FROM information_schema.tables
				WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'
				""",
				Integer.class);
		if (historyCount != null && historyCount > 0) {
			return;
		}

		boolean anyTablePresent = REQUIRED_LEGACY_TABLES.stream()
				.anyMatch(table -> tableExists(jdbcTemplate, table));
		if (!anyTablePresent) {
			return;
		}

		List<String> missing = REQUIRED_LEGACY_TABLES.stream()
				.filter(table -> !tableExists(jdbcTemplate, table))
				.toList();
		if (!missing.isEmpty()) {
			throw new IllegalStateException(
					"Legacy database baseline refused: incomplete Phase 0–3 schema, missing tables "
							+ missing
							+ ". Back up the database, fix schema manually, or use an empty database.");
		}
		log.info("Legacy schema compatibility verified before Flyway baseline");
	}

	private boolean tableExists(JdbcTemplate jdbcTemplate, String table) {
		Integer count = jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*) FROM information_schema.tables
				WHERE table_schema = 'public' AND table_name = ?
				""",
				Integer.class,
				table);
		return count != null && count > 0;
	}
}