package com.matchforge.common.events;

import java.time.Instant;
import java.util.UUID;

public record SessionCreatedEvent(UUID sessionId, UUID matchId, Instant occurredAt) {
}