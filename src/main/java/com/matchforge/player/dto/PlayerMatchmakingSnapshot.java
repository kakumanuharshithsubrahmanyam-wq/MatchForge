package com.matchforge.player.dto;

import java.util.UUID;

import com.matchforge.player.domain.PlayerStatus;

public record PlayerMatchmakingSnapshot(UUID id, int skillRating, PlayerStatus status) {
}