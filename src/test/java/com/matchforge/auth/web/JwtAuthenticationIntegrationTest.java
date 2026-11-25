package com.matchforge.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.matchforge.auth.config.SecurityProperties;
import com.matchforge.support.AuthTestHelper;
import com.matchforge.support.AuthTestHelper.RegisteredPlayer;
import com.matchforge.support.TestJwtHelper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthenticationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SecurityProperties securityProperties;

	@Test
	void validJwtAccepted() throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, "jwt_valid");
		mockMvc.perform(get("/api/v1/players/" + player.playerId()).with(AuthTestHelper.bearer(player.token())))
				.andExpect(status().isOk());
	}

	@Test
	void expiredJwtRejected() throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, "jwt_expired");
		String token = TestJwtHelper.expiredPlayerToken(
				securityProperties,
				UUID.fromString(player.accountId()),
				UUID.fromString(player.playerId()));
		mockMvc.perform(get("/api/v1/players/" + player.playerId()).with(AuthTestHelper.bearer(token)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void wrongIssuerRejected() throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, "jwt_issuer");
		String token = TestJwtHelper.wrongIssuerToken(
				securityProperties,
				UUID.fromString(player.accountId()),
				UUID.fromString(player.playerId()));
		mockMvc.perform(get("/api/v1/players/" + player.playerId()).with(AuthTestHelper.bearer(token)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void wrongSignatureRejected() throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, "jwt_sig");
		String token = TestJwtHelper.wrongSignatureToken(
				securityProperties,
				UUID.fromString(player.accountId()),
				UUID.fromString(player.playerId()));
		mockMvc.perform(get("/api/v1/players/" + player.playerId()).with(AuthTestHelper.bearer(token)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void invalidRoleRejected() throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, "jwt_role");
		String token = TestJwtHelper.invalidRoleToken(securityProperties, UUID.fromString(player.accountId()));
		mockMvc.perform(get("/api/v1/players").with(AuthTestHelper.bearer(token)))
				.andExpect(status().isForbidden());
	}
}