package com.matchforge.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class AuthorizationIntegrationTest {

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
	void playerReadsOwnProfile() throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, "authz_self");
		mockMvc.perform(get("/api/v1/players/" + player.playerId()).with(AuthTestHelper.bearer(player.token())))
				.andExpect(status().isOk());
	}

	@Test
	void playerCannotReadOtherProfile() throws Exception {
		RegisteredPlayer a = AuthTestHelper.registerPlayer(mockMvc, "authz_a");
		RegisteredPlayer b = AuthTestHelper.registerPlayer(mockMvc, "authz_b");
		mockMvc.perform(get("/api/v1/players/" + b.playerId()).with(AuthTestHelper.bearer(a.token())))
				.andExpect(status().isForbidden());
	}

	@Test
	void playerCannotListPlayers() throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, "authz_list");
		mockMvc.perform(get("/api/v1/players").with(AuthTestHelper.bearer(player.token())))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanListPlayers() throws Exception {
		AuthTestHelper.registerPlayer(mockMvc, "authz_admin_list");
		String adminToken = AuthTestHelper.loginAdmin(mockMvc);
		mockMvc.perform(get("/api/v1/players").with(AuthTestHelper.bearer(adminToken))).andExpect(status().isOk());
	}

	@Test
	void playerCannotChangeOtherAvailability() throws Exception {
		RegisteredPlayer a = registerAvailable("avail_a");
		RegisteredPlayer b = registerAvailable("avail_b");
		mockMvc.perform(put("/api/v1/players/" + b.playerId() + "/availability")
						.with(AuthTestHelper.bearer(a.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"available\":false}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void playerCannotPatchOtherProfile() throws Exception {
		RegisteredPlayer a = AuthTestHelper.registerPlayer(mockMvc, "patch_a");
		RegisteredPlayer b = AuthTestHelper.registerPlayer(mockMvc, "patch_b");
		mockMvc.perform(patch("/api/v1/players/" + b.playerId())
						.with(AuthTestHelper.bearer(a.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"skillRating\":2000}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void playerJoinsOwnQueueOnly() throws Exception {
		RegisteredPlayer a = registerAvailable("queue_a");
		RegisteredPlayer b = registerAvailable("queue_b");
		mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(a.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + b.playerId() + "\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void playerLeavesOwnQueue() throws Exception {
		RegisteredPlayer player = registerAvailable("leave_own");
		mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(player.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + player.playerId() + "\"}"))
				.andExpect(status().isAccepted());
		mockMvc.perform(delete("/api/v1/matchmaking/queue/" + player.playerId()).with(AuthTestHelper.bearer(player.token())))
				.andExpect(status().isOk());
	}

	@Test
	void playerCannotLeaveOtherQueue() throws Exception {
		RegisteredPlayer a = registerAvailable("leave_a");
		RegisteredPlayer b = registerAvailable("leave_b");
		mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(b.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + b.playerId() + "\"}"))
				.andExpect(status().isAccepted());
		mockMvc.perform(delete("/api/v1/matchmaking/queue/" + b.playerId()).with(AuthTestHelper.bearer(a.token())))
				.andExpect(status().isForbidden());
	}

	@Test
	void participantCanViewMatch() throws Exception {
		MatchContext match = createMatch();
		mockMvc.perform(get("/api/v1/matches/" + match.matchId()).with(AuthTestHelper.bearer(match.playerA().token())))
				.andExpect(status().isOk());
	}

	@Test
	void nonParticipantCannotViewMatch() throws Exception {
		MatchContext match = createMatch();
		RegisteredPlayer outsider = AuthTestHelper.registerPlayer(mockMvc, "match_outsider");
		mockMvc.perform(get("/api/v1/matches/" + match.matchId()).with(AuthTestHelper.bearer(outsider.token())))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanViewMatch() throws Exception {
		MatchContext match = createMatch();
		String admin = AuthTestHelper.loginAdmin(mockMvc);
		mockMvc.perform(get("/api/v1/matches/" + match.matchId()).with(AuthTestHelper.bearer(admin)))
				.andExpect(status().isOk());
	}

	@Test
	void nonParticipantCannotViewSession() throws Exception {
		MatchContext match = createMatch();
		RegisteredPlayer outsider = AuthTestHelper.registerPlayer(mockMvc, "session_outsider");
		mockMvc.perform(get("/api/v1/sessions/" + match.sessionId()).with(AuthTestHelper.bearer(outsider.token())))
				.andExpect(status().isForbidden());
	}

	@Test
	void participantCannotImpersonateOnJoin() throws Exception {
		MatchContext match = createMatch();
		mockMvc.perform(post("/api/v1/sessions/" + match.sessionId() + "/join")
						.with(AuthTestHelper.bearer(match.playerA().token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + match.playerB().playerId() + "\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void playerCannotCompleteSession() throws Exception {
		MatchContext match = createMatch();
		mockMvc.perform(post("/api/v1/sessions/" + match.sessionId() + "/complete")
						.with(AuthTestHelper.bearer(match.playerA().token())))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanCompleteSession() throws Exception {
		MatchContext match = createMatch();
		joinBoth(match);
		String admin = AuthTestHelper.loginAdmin(mockMvc);
		mockMvc.perform(post("/api/v1/sessions/" + match.sessionId() + "/complete").with(AuthTestHelper.bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("COMPLETED")));
	}

	private RegisteredPlayer registerAvailable(String username) throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, username);
		mockMvc.perform(put("/api/v1/players/" + player.playerId() + "/availability")
						.with(AuthTestHelper.bearer(player.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"available\":true}"))
				.andExpect(status().isOk());
		return player;
	}

	private MatchContext createMatch() throws Exception {
		RegisteredPlayer a = registerAvailable("match_ctx_a");
		RegisteredPlayer b = registerAvailable("match_ctx_b");
		mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(a.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + a.playerId() + "\"}"))
				.andExpect(status().isAccepted());
		MvcResult matched = mockMvc.perform(post("/api/v1/matchmaking/queue")
						.with(AuthTestHelper.bearer(b.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + b.playerId() + "\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.state", is("MATCHED")))
				.andReturn();
		String matchId = JsonPath.read(matched.getResponse().getContentAsString(), "$.matchId");
		MvcResult matchDetails = mockMvc.perform(get("/api/v1/matches/" + matchId).with(AuthTestHelper.bearer(a.token())))
				.andExpect(status().isOk())
				.andReturn();
		String sessionId = JsonPath.read(matchDetails.getResponse().getContentAsString(), "$.sessionId");
		return new MatchContext(a, b, matchId, sessionId);
	}

	private void joinBoth(MatchContext match) throws Exception {
		mockMvc.perform(post("/api/v1/sessions/" + match.sessionId() + "/join")
						.with(AuthTestHelper.bearer(match.playerA().token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + match.playerA().playerId() + "\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/sessions/" + match.sessionId() + "/join")
						.with(AuthTestHelper.bearer(match.playerB().token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + match.playerB().playerId() + "\"}"))
				.andExpect(status().isOk());
	}

	private record MatchContext(RegisteredPlayer playerA, RegisteredPlayer playerB, String matchId, String sessionId) {
	}
}