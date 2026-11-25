package com.matchforge.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Size(min = 3, max = 32) @Pattern(regexp = "^[A-Za-z0-9_]+$") String username,
		@NotBlank String password,
		@Min(0) @Max(10000) Integer skillRating) {
}