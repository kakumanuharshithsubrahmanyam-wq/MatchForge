package com.matchforge.session.dto;

import java.time.Instant;
import java.util.UUID;

import com.matchforge.session.domain.ParticipantStatus;
import com.matchforge.session.domain.SessionParticipant;

public record SessionParticipantResponse(
		UUID id,
		UUID playerId,
		ParticipantStatus status,
		Instant joinedAt,
		Instant lastHeartbeatAt,
		Instant disconnectedAt,
		Instant leftAt) {

	public static SessionParticipantResponse from(SessionParticipant participant) {
		return new SessionParticipantResponse(
				participant.getId(),
				participant.getPlayerId(),
				participant.getStatus(),
				participant.getJoinedAt(),
				participant.getLastHeartbeatAt(),
				participant.getDisconnectedAt(),
				participant.getLeftAt());
	}
}