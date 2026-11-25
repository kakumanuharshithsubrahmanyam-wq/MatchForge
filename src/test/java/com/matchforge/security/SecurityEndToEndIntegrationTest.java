package com.matchforge.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.matchforge.support.AuthTestHelper;
import com.matchforge.support.AuthTestHelper.RegisteredPlayer;
import com.matchforge.support.IntegrationTestDatabaseCleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityEndToEndIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private com.matchforge.auth.repository.AccountRepository accountRepository;

	@Autowired
	private com.matchforge.player.repository.PlayerRepository playerRepository;

	@Autowired
	private com.matchforge.matchmaking.repository.QueueEntryRepository queueEntryRepository;

	@Autowired
	private com.matchforge.matchmaking.repository.MatchRecordRepository matchRecordRepository;

	@Autowired
	private com.matchforge.session.repository.GameSessionRepository gameSessionRepository;

	@Autowired
	private com.matchforge.session.repository.SessionParticipantRepository sessionParticipantRepository;

	@BeforeEach
	void clean() {
		IntegrationTestDatabaseCleaner.resetGameplayData(
				sessionParticipantRepository,
				gameSessionRepository,
				queueEntryRepository,
				matchRecordRepository,
				accountRepository,
				playerRepository);
	}

	@Test
	void securedMatchmakingToAdminCompletion() throws Exception {
		RegisteredPlayer playerA = registerAndLogin("e2e_a");
		RegisteredPlayer playerB = registerAndLogin("e2e_b");
		RegisteredPlayer outsider = AuthTestHelper.registerPlayer(mockMvc, "e2e_out");

		mockMvc.perform(get("/api/v1/players/" + playerA.playerId()).with(AuthTestHelper.bearer(playerA.token())))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/players/" + playerB.playerId()).with(AuthTestHelper.bearer(playerA.token())))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/players/" + playerA.playerId() + "/availability")
						.with(AuthTestHelper.bearer(playerA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"available\":true}"))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/players/" + playerB.playerId() + "/availability")
						.with(AuthTestHelper.bearer(playerB.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"available\":true}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(playerA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerA.playerId() + "\"}"))
				.andExpect(status().isAccepted());
		MvcResult matched = mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(playerB.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerB.playerId() + "\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.state", is("MATCHED")))
				.andReturn();
		String matchId = JsonPath.read(matched.getResponse().getContentAsString(), "$.matchId");
		MvcResult match = mockMvc.perform(get("/api/v1/matches/" + matchId).with(AuthTestHelper.bearer(playerA.token())))
				.andExpect(status().isOk())
				.andReturn();
		String sessionId = JsonPath.read(match.getResponse().getContentAsString(), "$.sessionId");

		mockMvc.perform(get("/api/v1/sessions/" + sessionId).with(AuthTestHelper.bearer(outsider.token())))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/join")
						.with(AuthTestHelper.bearer(playerA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerA.playerId() + "\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/join")
						.with(AuthTestHelper.bearer(playerB.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerB.playerId() + "\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/heartbeat")
						.with(AuthTestHelper.bearer(playerA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerA.playerId() + "\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/complete")
						.with(AuthTestHelper.bearer(playerA.token())))
				.andExpect(status().isForbidden());

		String adminToken = AuthTestHelper.loginAdmin(mockMvc);
		mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/complete").with(AuthTestHelper.bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("COMPLETED")));

		mockMvc.perform(get("/api/v1/players/" + playerA.playerId()).with(AuthTestHelper.bearer(playerA.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("AVAILABLE")));
	}

	private RegisteredPlayer registerAndLogin(String username) throws Exception {
		RegisteredPlayer registered = AuthTestHelper.registerPlayer(mockMvc, username);
		String loginToken = AuthTestHelper.loginPlayer(mockMvc, username, AuthTestHelper.DEFAULT_PASSWORD);
		return new RegisteredPlayer(registered.playerId(), registered.accountId(), loginToken);
	}
}