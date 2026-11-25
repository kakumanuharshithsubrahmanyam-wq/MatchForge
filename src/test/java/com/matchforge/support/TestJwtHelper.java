package com.matchforge.support;

import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.matchforge.auth.config.SecurityProperties;
import com.matchforge.auth.security.JwtTokenService;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

public final class TestJwtHelper {

	private TestJwtHelper() {
	}

	public static String expiredPlayerToken(SecurityProperties properties, UUID accountId, UUID playerId) {
		Instant expiredAt = Instant.now().minusSeconds(600);
		return encodeExpired(properties, accountId, playerId, "PLAYER", properties.getJwtIssuer(), expiredAt);
	}

	public static String wrongIssuerToken(SecurityProperties properties, UUID accountId, UUID playerId) {
		return encode(properties, accountId, playerId, "PLAYER", "wrong-issuer", Instant.now());
	}

	public static String wrongSignatureToken(SecurityProperties properties, UUID accountId, UUID playerId) {
		SecurityProperties copy = new SecurityProperties();
		copy.setJwtSecret(properties.getJwtSecret() + "-other-key-material-padding!!");
		copy.setJwtIssuer(properties.getJwtIssuer());
		copy.setJwtExpirationSeconds(properties.getJwtExpirationSeconds());
		return encode(copy, accountId, playerId, "PLAYER", copy.getJwtIssuer(), Instant.now());
	}

	public static String missingPlayerClaim(SecurityProperties properties, UUID accountId) {
		SecretKey key = JwtTokenService.secretKey(properties.getJwtSecret());
		JWKSource<SecurityContext> source = new ImmutableSecret<>(key);
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(source);
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(accountId.toString())
				.issuer(properties.getJwtIssuer())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(properties.getJwtExpirationSeconds()))
				.claim("role", "PLAYER")
				.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	public static String invalidRoleToken(SecurityProperties properties, UUID accountId) {
		return encode(properties, accountId, null, "SUPERUSER", properties.getJwtIssuer(), Instant.now());
	}

	private static String encode(
			SecurityProperties properties,
			UUID accountId,
			UUID playerId,
			String role,
			String issuer,
			Instant issuedAt) {
		return encodeExpired(
				properties,
				accountId,
				playerId,
				role,
				issuer,
				issuedAt,
				issuedAt.plusSeconds(properties.getJwtExpirationSeconds()));
	}

	private static String encodeExpired(
			SecurityProperties properties,
			UUID accountId,
			UUID playerId,
			String role,
			String issuer,
			Instant issuedAt,
			Instant expiresAt) {
		SecretKey key = JwtTokenService.secretKey(properties.getJwtSecret());
		JWKSource<SecurityContext> source = new ImmutableSecret<>(key);
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(source);
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.subject(accountId.toString())
				.issuer(issuer)
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim("role", role);
		if (playerId != null) {
			claims.claim("playerId", playerId.toString());
		}
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
				.getTokenValue();
	}

	private static String encodeExpired(
			SecurityProperties properties,
			UUID accountId,
			UUID playerId,
			String role,
			String issuer,
			Instant expiresAt) {
		return encodeExpired(
				properties,
				accountId,
				playerId,
				role,
				issuer,
				expiresAt.minusSeconds(120),
				expiresAt);
	}
}