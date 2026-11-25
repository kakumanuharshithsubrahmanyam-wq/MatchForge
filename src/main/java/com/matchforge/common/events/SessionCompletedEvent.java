package com.matchforge.common.events;

import java.time.Instant;
import java.util.UUID;

public record SessionCompletedEvent(UUID sessionId, Instant occurredAt) {
}