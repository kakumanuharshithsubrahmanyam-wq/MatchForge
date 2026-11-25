package com.matchforge.matchmaking.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.matchforge.common.config.JpaAuditingConfig;
import com.matchforge.matchmaking.domain.QueueEntry;
import com.matchforge.matchmaking.domain.QueueEntryStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
class QueueEntryRepositoryTest {

	@Autowired
	private QueueEntryRepository queueEntryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void ensurePartialUniqueIndex() {
		jdbcTemplate.execute(
				"""
				CREATE UNIQUE INDEX IF NOT EXISTS ux_queue_entries_active_player
				ON queue_entries (player_id)
				WHERE status = 'QUEUED'
				""");
	}

	@Test
	void cancelledEntryAllowsFutureQueueEntry() {
		UUID playerId = UUID.randomUUID();

		QueueEntry first = activeEntry(playerId, 1000);
		queueEntryRepository.saveAndFlush(first);

		first.setStatus(QueueEntryStatus.CANCELLED);
		queueEntryRepository.saveAndFlush(first);

		QueueEntry second = activeEntry(playerId, 1000);
		queueEntryRepository.saveAndFlush(second);

		assertThat(queueEntryRepository.findByPlayerIdAndStatus(playerId, QueueEntryStatus.QUEUED))
				.isPresent();
	}

	@Test
	void duplicateActiveQueueEntryViolatesConstraint() {
		UUID playerId = UUID.randomUUID();
		queueEntryRepository.saveAndFlush(activeEntry(playerId, 1000));

		assertThatThrownBy(() -> queueEntryRepository.saveAndFlush(activeEntry(playerId, 1100)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findByPlayerIdAndStatus_filtersCorrectly() {
		UUID playerId = UUID.randomUUID();
		QueueEntry entry = activeEntry(playerId, 1100);
		queueEntryRepository.saveAndFlush(entry);

		assertThat(queueEntryRepository.findByPlayerIdAndStatus(playerId, QueueEntryStatus.QUEUED))
				.isPresent();
	}

	private QueueEntry activeEntry(UUID playerId, int rating) {
		QueueEntry entry = new QueueEntry();
		entry.setPlayerId(playerId);
		entry.setSkillRatingAtQueue(rating);
		entry.setStatus(QueueEntryStatus.QUEUED);
		return entry;
	}
}