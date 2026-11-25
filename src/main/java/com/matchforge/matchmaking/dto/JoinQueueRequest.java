package com.matchforge.matchmaking.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record JoinQueueRequest(@NotNull(message = "playerId is required") UUID playerId) {
}