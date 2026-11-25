package com.matchforge.auth.dto;

import java.util.UUID;

import com.matchforge.auth.domain.Account;
import com.matchforge.auth.domain.AccountRole;

public record AuthResponse(
		String accessToken,
		String tokenType,
		long expiresInSeconds,
		UUID accountId,
		AccountRole role,
		UUID playerId) {

	public static AuthResponse bearer(String token, long expiresInSeconds, UUID playerId, Account account) {
		return new AuthResponse(token, "Bearer", expiresInSeconds, account.getId(), account.getRole(), playerId);
	}
}