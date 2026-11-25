package com.matchforge.player.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.matchforge.auth.repository.AccountRepository;
import com.matchforge.matchmaking.repository.MatchRecordRepository;
import com.matchforge.matchmaking.repository.QueueEntryRepository;
import com.matchforge.player.repository.PlayerRepository;
import com.matchforge.session.repository.GameSessionRepository;
import com.matchforge.session.repository.SessionParticipantRepository;
import com.matchforge.support.AuthTestHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlayerControllerIntegrationTest {

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
	void cleanPlayers() {
		com.matchforge.support.IntegrationTestDatabaseCleaner.resetGameplayData(
				participantRepository,
				gameSessionRepository,
				queueEntryRepository,
				matchRecordRepository,
				accountRepository,
				playerRepository);
	}

	@Test
	void register_success_returns201() throws Exception {
		var registered = AuthTestHelper.registerPlayer(mockMvc, "Player_One", AuthTestHelper.DEFAULT_PASSWORD, 1200);

		mockMvc.perform(get("/api/v1/players/{id}", registered.playerId()).with(AuthTestHelper.bearer(registered.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("player_one"))
				.andExpect(jsonPath("$.skillRating").value(1200))
				.andExpect(jsonPath("$.status").value("OFFLINE"));
	}

	@Test
	void register_duplicateUsername_returns409() throws Exception {
		AuthTestHelper.registerPlayer(mockMvc, "dupe");
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"DUPE\",\"password\":\"" + AuthTestHelper.DEFAULT_PASSWORD + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void register_blankUsername_returns400() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"   \",\"password\":\"" + AuthTestHelper.DEFAULT_PASSWORD + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void register_invalidUsername_returns400() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"bad name\",\"password\":\"" + AuthTestHelper.DEFAULT_PASSWORD + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors").isArray());
	}

	@Test
	void getPlayer_existing_returns200() throws Exception {
		var registered = AuthTestHelper.registerPlayer(mockMvc, "getter");

		mockMvc.perform(get("/api/v1/players/{id}", registered.playerId()).with(AuthTestHelper.bearer(registered.token())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("getter"));
	}

	@Test
	void getPlayer_otherPlayerId_returns403() throws Exception {
		var registered = AuthTestHelper.registerPlayer(mockMvc, "missing_lookup");
		mockMvc.perform(get("/api/v1/players/{id}", UUID.randomUUID()).with(AuthTestHelper.bearer(registered.token())))
				.andExpect(status().isForbidden());
	}

	@Test
	void listPlayers_returnsAll() throws Exception {
		AuthTestHelper.registerPlayer(mockMvc, "list_a");
		AuthTestHelper.registerPlayer(mockMvc, "list_b");
		String adminToken = AuthTestHelper.loginAdmin(mockMvc);

		mockMvc.perform(get("/api/v1/players").with(AuthTestHelper.bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void setAvailability_validTransition_updatesStatus() throws Exception {
		var registered = AuthTestHelper.registerPlayer(mockMvc, "avail_user");

		mockMvc.perform(put("/api/v1/players/{id}/availability", registered.playerId())
						.with(AuthTestHelper.bearer(registered.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"available\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AVAILABLE"));

		mockMvc.perform(put("/api/v1/players/{id}/availability", registered.playerId())
						.with(AuthTestHelper.bearer(registered.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"available\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OFFLINE"));
	}

	@Test
	void patchProfile_updatesSkillRating() throws Exception {
		var registered = AuthTestHelper.registerPlayer(mockMvc, "patch_me");

		mockMvc.perform(patch("/api/v1/players/{id}", registered.playerId())
						.with(AuthTestHelper.bearer(registered.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"skillRating\":1500}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skillRating").value(1500));
	}
}