package com.matchforge.matchmaking.dto;

import java.time.Instant;
import java.util.UUID;

public record QueueStatusResponse(
		UUID playerId,
		QueueState state,
		UUID queueEntryId,
		UUID matchId,
		Integer skillRatingAtQueue,
		Instant queuedAt) {
}