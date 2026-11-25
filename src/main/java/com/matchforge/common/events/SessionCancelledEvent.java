package com.matchforge.common.events;

import java.time.Instant;
import java.util.UUID;

public record SessionCancelledEvent(UUID sessionId, Instant occurredAt) {
}