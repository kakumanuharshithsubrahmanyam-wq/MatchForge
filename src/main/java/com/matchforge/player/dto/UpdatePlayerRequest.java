package com.matchforge.player.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdatePlayerRequest(
		@Min(value = 0, message = "Skill rating must be at least 0")
				@Max(value = 10000, message = "Skill rating must be at most 10000")
				Integer skillRating) {
}