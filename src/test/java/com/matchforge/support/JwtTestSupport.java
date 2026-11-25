package com.matchforge.support;

import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class JwtTestSupport {

	private JwtTestSupport() {
	}

	public static RequestPostProcessor playerJwt(UUID accountId, UUID playerId) {
		return SecurityMockMvcRequestPostProcessors.jwt()
				.jwt(jwt -> jwt.subject(accountId.toString())
						.claim("role", "PLAYER")
						.claim("playerId", playerId.toString())
						.issuer("matchforge"))
				.authorities(new SimpleGrantedAuthority("ROLE_PLAYER"));
	}

	public static RequestPostProcessor adminJwt(UUID accountId) {
		return SecurityMockMvcRequestPostProcessors.jwt()
				.jwt(jwt -> jwt.subject(accountId.toString()).claim("role", "ADMIN").issuer("matchforge"))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}
}