package com.matchforge.player.dto;

import jakarta.validation.constraints.NotNull;

public record SetAvailabilityRequest(@NotNull(message = "available is required") Boolean available) {
}