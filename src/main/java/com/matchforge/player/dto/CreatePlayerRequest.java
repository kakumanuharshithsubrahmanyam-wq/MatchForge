package com.matchforge.player.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePlayerRequest(
		@NotBlank(message = "Username is required")
				@Size(min = 3, max = 32, message = "Username must be between 3 and 32 characters")
				@Pattern(
						regexp = "^[A-Za-z0-9_]+$",
						message = "Username may only contain letters, numbers, and underscores")
				String username,
		@Min(value = 0, message = "Skill rating must be at least 0")
				@Max(value = 10000, message = "Skill rating must be at most 10000")
				Integer skillRating) {
}