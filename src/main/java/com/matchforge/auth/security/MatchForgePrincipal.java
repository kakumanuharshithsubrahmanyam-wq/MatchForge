package com.matchforge.auth.security;

import java.util.UUID;

import com.matchforge.auth.domain.AccountRole;

public record MatchForgePrincipal(UUID accountId, AccountRole role, UUID playerId) {
}