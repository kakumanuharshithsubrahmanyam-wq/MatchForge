package com.matchforge.auth.security;

import java.util.UUID;

import com.matchforge.auth.domain.AccountRole;
import com.matchforge.common.error.ConflictException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class SecurityContextSupport {

	private SecurityContextSupport() {
	}

	public static MatchForgePrincipal requirePrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			throw new ConflictException("Authentication required");
		}
		UUID accountId = UUID.fromString(jwt.getSubject());
		AccountRole role = AccountRole.valueOf(jwt.getClaimAsString("role"));
		String playerId = jwt.getClaimAsString("playerId");
		UUID playerUuid = playerId == null || playerId.isBlank() ? null : UUID.fromString(playerId);
		return new MatchForgePrincipal(accountId, role, playerUuid);
	}

	public static boolean isAdmin() {
		return currentPrincipal().map(principal -> principal.role() == AccountRole.ADMIN).orElse(false);
	}

	public static java.util.Optional<MatchForgePrincipal> currentPrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			return java.util.Optional.empty();
		}
		UUID accountId = UUID.fromString(jwt.getSubject());
		AccountRole role = AccountRole.valueOf(jwt.getClaimAsString("role"));
		String playerId = jwt.getClaimAsString("playerId");
		UUID playerUuid = playerId == null || playerId.isBlank() ? null : UUID.fromString(playerId);
		return java.util.Optional.of(new MatchForgePrincipal(accountId, role, playerUuid));
	}

	public static UUID requirePlayerId() {
		MatchForgePrincipal principal = requirePrincipal();
		if (principal.playerId() == null) {
			throw new ConflictException("Player account required");
		}
		return principal.playerId();
	}

	public static void requireSelfOrAdmin(UUID playerId) {
		MatchForgePrincipal principal = requirePrincipal();
		if (principal.role() == AccountRole.ADMIN) {
			return;
		}
		if (principal.playerId() == null || !principal.playerId().equals(playerId)) {
			throw new org.springframework.security.access.AccessDeniedException("Forbidden");
		}
	}
}