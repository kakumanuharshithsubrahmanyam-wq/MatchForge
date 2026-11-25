package com.matchforge.matchmaking.dto;

import java.time.Instant;
import java.util.UUID;

import com.matchforge.matchmaking.domain.MatchRecord;
import com.matchforge.matchmaking.domain.MatchStatus;

public record MatchResponse(
		UUID id,
		UUID playerOneId,
		UUID playerTwoId,
		int playerOneRating,
		int playerTwoRating,
		MatchStatus status,
		UUID sessionId,
		Instant createdAt) {

	public static MatchResponse from(MatchRecord match, UUID sessionId) {
		return new MatchResponse(
				match.getId(),
				match.getPlayerOneId(),
				match.getPlayerTwoId(),
				match.getPlayerOneRating(),
				match.getPlayerTwoRating(),
				match.getStatus(),
				sessionId,
				match.getCreatedAt());
	}
}