package com.matchforge.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.matchforge.support.AuthTestHelper;
import com.matchforge.support.JwtTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityTest {

	private static final String PASSWORD = "StrongPassword-Example-1";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void unauthenticatedPlayerEndpointIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/players")).andExpect(status().isUnauthorized());
	}

	@Test
	void playerCannotListAllPlayers() throws Exception {
		var player = AuthTestHelper.registerPlayer(mockMvc, "auth_player_a", PASSWORD, null);
		mockMvc.perform(get("/api/v1/players").with(JwtTestSupport.playerJwt(
						java.util.UUID.fromString(player.accountId()), java.util.UUID.fromString(player.playerId()))))
				.andExpect(status().isForbidden());
	}

	@Test
	void weakPasswordRegistrationIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"weak_pw_user\",\"password\":\"short\"}"))
				.andExpect(status().isConflict());
	}
}