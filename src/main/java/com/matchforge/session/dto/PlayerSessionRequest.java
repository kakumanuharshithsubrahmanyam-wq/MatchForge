package com.matchforge.session.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record PlayerSessionRequest(@NotNull(message = "playerId is required") UUID playerId) {
}