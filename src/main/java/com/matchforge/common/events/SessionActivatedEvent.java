package com.matchforge.common.events;

import java.time.Instant;
import java.util.UUID;

public record SessionActivatedEvent(UUID sessionId, Instant occurredAt) {
}