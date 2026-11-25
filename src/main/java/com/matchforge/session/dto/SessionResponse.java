package com.matchforge.session.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.matchforge.session.domain.GameSession;
import com.matchforge.session.domain.SessionParticipant;
import com.matchforge.session.domain.SessionStatus;

public record SessionResponse(
		UUID id,
		UUID matchId,
		SessionStatus status,
		int maximumParticipants,
		Instant createdAt,
		Instant startedAt,
		Instant completedAt,
		Instant lastUpdatedAt,
		List<SessionParticipantResponse> participants) {

	public static SessionResponse from(GameSession session, List<SessionParticipant> participants) {
		return new SessionResponse(
				session.getId(),
				session.getMatchId(),
				session.getStatus(),
				session.getMaximumParticipants(),
				session.getCreatedAt(),
				session.getStartedAt(),
				session.getCompletedAt(),
				session.getUpdatedAt(),
				participants.stream().map(SessionParticipantResponse::from).toList());
	}
}