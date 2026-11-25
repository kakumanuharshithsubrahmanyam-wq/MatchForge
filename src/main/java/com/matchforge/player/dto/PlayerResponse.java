package com.matchforge.player.dto;

import java.time.Instant;
import java.util.UUID;

import com.matchforge.player.domain.Player;
import com.matchforge.player.domain.PlayerStatus;

public record PlayerResponse(
		UUID id,
		String username,
		int skillRating,
		PlayerStatus status,
		Instant createdAt,
		Instant updatedAt) {

	public static PlayerResponse from(Player player) {
		return new PlayerResponse(
				player.getId(),
				player.getUsername(),
				player.getSkillRating(),
				player.getStatus(),
				player.getCreatedAt(),
				player.getUpdatedAt());
	}
}