package com.matchforge.matchmaking.web;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class MatchmakingIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlayerRepository playerRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private QueueEntryRepository queueEntryRepository;

	@Autowired
	private MatchRecordRepository matchRecordRepository;

	@Autowired
	private GameSessionRepository gameSessionRepository;

	@Autowired
	private SessionParticipantRepository participantRepository;

	@BeforeEach
	void cleanDatabase() {
		com.matchforge.support.IntegrationTestDatabaseCleaner.resetGameplayData(
				participantRepository,
				gameSessionRepository,
				queueEntryRepository,
				matchRecordRepository,
				accountRepository,
				playerRepository);
	}

	@Test
	void twoPlayersJoinAndMatch() throws Exception {
		RegisteredPlayer playerOne = registerAvailablePlayer("mm_player_a");
		RegisteredPlayer playerTwo = registerAvailablePlayer("mm_player_b");

		mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(playerOne.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerOne.playerId() + "\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.state", is("QUEUED")));

		MvcResult matchResult = mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(playerTwo.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerTwo.playerId() + "\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.state", is("MATCHED")))
				.andExpect(jsonPath("$.matchId").exists())
				.andReturn();

		String matchId = JsonPath.read(matchResult.getResponse().getContentAsString(), "$.matchId");

		mockMvc.perform(get("/api/v1/matches/{matchId}", matchId).with(AuthTestHelper.bearer(playerOne.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.playerOneId").exists())
				.andExpect(jsonPath("$.playerTwoId").exists())
				.andExpect(jsonPath("$.status", is("PENDING")))
				.andExpect(jsonPath("$.sessionId").exists());
	}

	@Test
	void duplicateQueueEntryReturns409() throws Exception {
		RegisteredPlayer player = registerAvailablePlayer("dupe_queue");

		mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(player.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + player.playerId() + "\"}"))
				.andExpect(status().isAccepted());

		mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(player.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + player.playerId() + "\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void unavailablePlayerCannotJoin() throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, "offline_player");

		mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(player.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + player.playerId() + "\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void leaveQueueReturnsPlayerToAvailable() throws Exception {
		RegisteredPlayer player = registerAvailablePlayer("leave_queue");

		mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(player.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + player.playerId() + "\"}"))
				.andExpect(status().isAccepted());

		mockMvc.perform(delete("/api/v1/matchmaking/queue/{playerId}", player.playerId())
						.with(AuthTestHelper.bearer(player.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.state", is("CANCELLED")));

		mockMvc.perform(get("/api/v1/matchmaking/queue/{playerId}", player.playerId())
						.with(AuthTestHelper.bearer(player.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.state", is("CANCELLED")));
	}

	private RegisteredPlayer registerAvailablePlayer(String username) throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, username);
		mockMvc.perform(put("/api/v1/players/{id}/availability", player.playerId())
						.with(AuthTestHelper.bearer(player.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"available\":true}"))
				.andExpect(status().isOk());
		return player;
	}
}