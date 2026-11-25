package com.matchforge.common.events;

import java.time.Instant;
import java.util.UUID;

public record MatchCreatedEvent(UUID matchId, Instant occurredAt) {
}