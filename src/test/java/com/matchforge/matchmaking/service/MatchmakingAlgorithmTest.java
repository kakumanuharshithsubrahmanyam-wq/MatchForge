package com.matchforge.matchmaking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.matchforge.matchmaking.config.MatchmakingProperties;
import com.matchforge.matchmaking.domain.QueueEntry;
import com.matchforge.matchmaking.domain.QueueEntryStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MatchmakingAlgorithmTest {

	private final MatchmakingAlgorithm algorithm = new MatchmakingAlgorithm();
	private MatchmakingProperties properties;
	private Instant now;

	@BeforeEach
	void setUp() {
		properties = new MatchmakingProperties();
		properties.setInitialRatingDifference(100);
		properties.setRatingExpansion(50);
		properties.setExpansionIntervalSeconds(30);
		properties.setMaxRatingDifference(500);
		now = Instant.parse("2026-01-01T12:00:00Z");
	}

	@Test
	void selectsExactRatingMatchFirst() {
		QueueEntry subject = entry(1000, now);
		QueueEntry close = entry(1010, now);
		QueueEntry far = entry(1200, now);

		QueueEntry selected = algorithm.selectOpponent(subject, List.of(far, close), now, properties);
		assertThat(selected.getSkillRatingAtQueue()).isEqualTo(1010);
	}

	@Test
	void respectsInitialRatingWindow() {
		QueueEntry subject = entry(1000, now);
		QueueEntry opponent = entry(1150, now);

		assertThat(algorithm.isEligible(subject, opponent, now, properties)).isFalse();
	}

	@Test
	void expandsRatingWindowWithWaitTime() {
		QueueEntry subject = entry(1000, now.minusSeconds(60));
		QueueEntry opponent = entry(1150, now.minusSeconds(60));

		assertThat(algorithm.allowedRatingDifference(subject.getCreatedAt(), opponent.getCreatedAt(), now, properties))
				.isEqualTo(200);
		assertThat(algorithm.isEligible(subject, opponent, now, properties)).isTrue();
	}

	@Test
	void capsRatingWindowAtMaximum() {
		QueueEntry subject = entry(1000, now.minusSeconds(3600));
		QueueEntry opponent = entry(1800, now.minusSeconds(3600));

		assertThat(algorithm.allowedRatingDifference(subject.getCreatedAt(), opponent.getCreatedAt(), now, properties))
				.isEqualTo(500);
	}

	@Test
	void fifoTieBreakerUsesEarlierQueueTime() {
		QueueEntry subject = entry(1000, now);
		QueueEntry later = entry(1010, now.plusSeconds(5));
		QueueEntry earlier = entry(1010, now.minusSeconds(5));

		QueueEntry selected = algorithm.selectOpponent(subject, List.of(later, earlier), now, properties);
		assertThat(selected.getCreatedAt()).isEqualTo(earlier.getCreatedAt());
	}

	@Test
	void excludesIneligibleCancelledCandidates() {
		QueueEntry subject = entry(1000, now);
		QueueEntry cancelled = entry(1000, now);
		cancelled.setStatus(QueueEntryStatus.CANCELLED);

		assertThat(algorithm.selectOpponent(subject, List.of(cancelled), now, properties)).isNull();
	}

	@Test
	void returnsNullWhenNoOpponentAvailable() {
		QueueEntry subject = entry(1000, now);
		QueueEntry opponent = entry(2000, now);

		assertThat(algorithm.selectOpponent(subject, List.of(opponent), now, properties)).isNull();
	}

	private QueueEntry entry(int rating, Instant createdAt) {
		QueueEntry entry = new QueueEntry();
		entry.setPlayerId(UUID.randomUUID());
		entry.setSkillRatingAtQueue(rating);
		entry.setStatus(QueueEntryStatus.QUEUED);
		ReflectionTestUtils.setField(entry, "createdAt", createdAt);
		return entry;
	}
}