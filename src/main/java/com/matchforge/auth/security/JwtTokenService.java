package com.matchforge.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.matchforge.auth.config.SecurityProperties;
import com.matchforge.auth.domain.Account;
import com.matchforge.auth.domain.AccountRole;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Service
public class JwtTokenService {

	private final SecurityProperties securityProperties;
	private final JwtEncoder jwtEncoder;
	private final JwtDecoder jwtDecoder;

	public JwtTokenService(SecurityProperties securityProperties) {
		this.securityProperties = securityProperties;
		SecretKey secretKey = secretKey(securityProperties.getJwtSecret());
		JWKSource<SecurityContext> source = new ImmutableSecret<>(secretKey);
		this.jwtEncoder = new NimbusJwtEncoder(source);
		NimbusJwtDecoder decoder =
				NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(securityProperties.getJwtIssuer()));
		this.jwtDecoder = decoder;
	}

	public String createToken(Account account) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(securityProperties.getJwtExpirationSeconds());
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.subject(account.getId().toString())
				.issuer(securityProperties.getJwtIssuer())
				.issuedAt(now)
				.expiresAt(expiresAt)
				.claim("role", account.getRole().name());
		if (account.getPlayerId() != null) {
			claims.claim("playerId", account.getPlayerId().toString());
		}
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

	public JwtDecoder jwtDecoder() {
		return jwtDecoder;
	}

	public static SecretKey secretKey(String secret) {
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("JWT secret must be at least 256 bits");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}
}