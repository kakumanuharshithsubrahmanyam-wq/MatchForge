package com.matchforge.session.web;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.matchforge.auth.repository.AccountRepository;
import com.matchforge.matchmaking.repository.MatchRecordRepository;
import com.matchforge.matchmaking.repository.QueueEntryRepository;
import com.matchforge.player.repository.PlayerRepository;
import com.matchforge.session.repository.GameSessionRepository;
import com.matchforge.session.repository.SessionParticipantRepository;
import com.matchforge.support.AuthTestHelper;
import com.matchforge.support.AuthTestHelper.RegisteredPlayer;

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
class SessionFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private GameSessionRepository gameSessionRepository;

	@Autowired
	private SessionParticipantRepository participantRepository;

	@Autowired
	private QueueEntryRepository queueEntryRepository;

	@Autowired
	private MatchRecordRepository matchRecordRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private PlayerRepository playerRepository;

	@BeforeEach
	void clean() {
		com.matchforge.support.IntegrationTestDatabaseCleaner.resetGameplayData(
				participantRepository,
				gameSessionRepository,
				queueEntryRepository,
				matchRecordRepository,
				accountRepository,
				playerRepository);
	}

	@Test
	void fullPlayerToMatchToSessionFlow() throws Exception {
		RegisteredPlayer playerA = registerAvailable("flow_a");
		RegisteredPlayer playerB = registerAvailable("flow_b");
		String adminToken = AuthTestHelper.loginAdmin(mockMvc);

		mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(playerA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerA.playerId() + "\"}"))
				.andExpect(status().isAccepted());

		MvcResult matchJoin = mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(playerB.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerB.playerId() + "\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.state", is("MATCHED")))
				.andReturn();

		String matchId = JsonPath.read(matchJoin.getResponse().getContentAsString(), "$.matchId");

		MvcResult matchDetails = mockMvc.perform(get("/api/v1/matches/{matchId}", matchId)
						.with(AuthTestHelper.bearer(playerA.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sessionId").exists())
				.andReturn();

		String sessionId = JsonPath.read(matchDetails.getResponse().getContentAsString(), "$.sessionId");

		mockMvc.perform(post("/api/v1/sessions/{sessionId}/join", sessionId)
						.with(AuthTestHelper.bearer(playerA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerA.playerId() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("WAITING_FOR_PLAYERS")));

		mockMvc.perform(post("/api/v1/sessions/{sessionId}/join", sessionId)
						.with(AuthTestHelper.bearer(playerB.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerB.playerId() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("ACTIVE")));

		mockMvc.perform(post("/api/v1/sessions/{sessionId}/heartbeat", sessionId)
						.with(AuthTestHelper.bearer(playerA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerA.playerId() + "\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/sessions/{sessionId}/heartbeat", sessionId)
						.with(AuthTestHelper.bearer(playerB.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerB.playerId() + "\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/sessions/{sessionId}/disconnect", sessionId)
						.with(AuthTestHelper.bearer(playerA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerA.playerId() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("PAUSED")));

		mockMvc.perform(post("/api/v1/sessions/{sessionId}/reconnect", sessionId)
						.with(AuthTestHelper.bearer(playerA.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerA.playerId() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("ACTIVE")));

		mockMvc.perform(post("/api/v1/sessions/{sessionId}/complete", sessionId).with(AuthTestHelper.bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("COMPLETED")));

		mockMvc.perform(get("/api/v1/players/{id}", playerA.playerId()).with(AuthTestHelper.bearer(playerA.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("AVAILABLE")));
	}

	private RegisteredPlayer registerAvailable(String username) throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, username);
		mockMvc.perform(put("/api/v1/players/{id}/availability", player.playerId())
						.with(AuthTestHelper.bearer(player.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"available\":true}"))
				.andExpect(status().isOk());
		return player;
	}
}