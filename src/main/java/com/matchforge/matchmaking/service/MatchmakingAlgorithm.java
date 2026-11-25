package com.matchforge.matchmaking.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.matchforge.matchmaking.config.MatchmakingProperties;
import com.matchforge.matchmaking.domain.QueueEntry;
import com.matchforge.matchmaking.domain.QueueEntryStatus;

/**
 * Pure skill-window and FIFO pairing logic. No database access.
 */
public class MatchmakingAlgorithm {

	public QueueEntry selectOpponent(QueueEntry subject, List<QueueEntry> opponents, Instant now, MatchmakingProperties props) {
		Comparator<QueueEntry> bestOpponentOrder = Comparator.<QueueEntry>comparingInt(
						candidate -> Math.abs(subject.getSkillRatingAtQueue() - candidate.getSkillRatingAtQueue()))
				.thenComparing(QueueEntry::getCreatedAt);

		return opponents.stream()
				.filter(Objects::nonNull)
				.filter(candidate -> candidate.getStatus() == QueueEntryStatus.QUEUED)
				.filter(candidate -> !candidate.getPlayerId().equals(subject.getPlayerId()))
				.filter(candidate -> isEligible(subject, candidate, now, props))
				.min(bestOpponentOrder)
				.orElse(null);
	}

	public boolean isEligible(QueueEntry left, QueueEntry right, Instant now, MatchmakingProperties props) {
		int ratingDiff = Math.abs(left.getSkillRatingAtQueue() - right.getSkillRatingAtQueue());
		int allowed = allowedRatingDifference(left.getCreatedAt(), right.getCreatedAt(), now, props);
		return ratingDiff <= allowed;
	}

	public int allowedRatingDifference(Instant leftQueuedAt, Instant rightQueuedAt, Instant now, MatchmakingProperties props) {
		long leftWait = Math.max(0, Duration.between(leftQueuedAt, now).getSeconds());
		long rightWait = Math.max(0, Duration.between(rightQueuedAt, now).getSeconds());
		long longestWait = Math.max(leftWait, rightWait);

		long expansionSteps = longestWait / props.getExpansionIntervalSeconds();
		long expanded = (long) props.getInitialRatingDifference() + expansionSteps * props.getRatingExpansion();
		return (int) Math.min(expanded, props.getMaxRatingDifference());
	}
}