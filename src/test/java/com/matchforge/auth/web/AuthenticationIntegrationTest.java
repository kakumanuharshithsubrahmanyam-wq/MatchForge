package com.matchforge.auth.web;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matchforge.auth.domain.Account;
import com.matchforge.auth.repository.AccountRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
	"matchforge.security.max-failed-login-attempts=3",
	"matchforge.security.login-lockout-seconds=600"
})
class AuthenticationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountRepository accountRepository;

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
	void register_success() throws Exception {
		AuthTestHelper.registerPlayer(mockMvc, "reg_success");
	}

	@Test
	void register_duplicateUsername() throws Exception {
		AuthTestHelper.registerPlayer(mockMvc, "dupe_auth");
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"DUPE_AUTH\",\"password\":\"" + AuthTestHelper.DEFAULT_PASSWORD + "\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void register_invalidUsername() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"bad name\",\"password\":\"" + AuthTestHelper.DEFAULT_PASSWORD + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void register_weakPassword() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"weak_user\",\"password\":\"short\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void login_success() throws Exception {
		AuthTestHelper.registerPlayer(mockMvc, "login_ok");
		AuthTestHelper.loginPlayer(mockMvc, "login_ok", AuthTestHelper.DEFAULT_PASSWORD);
	}

	@Test
	void login_wrongPassword_genericMessage() throws Exception {
		AuthTestHelper.registerPlayer(mockMvc, "login_wrong");
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"login_wrong\",\"password\":\"WrongPassword-1\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message", equalTo("Invalid credentials")));
	}

	@Test
	void login_unknownUser_sameMessageAsWrongPassword() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"nobody\",\"password\":\"" + AuthTestHelper.DEFAULT_PASSWORD + "\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message", equalTo("Invalid credentials")));
	}

	@Test
	void login_disabledAccount() throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, "disabled_user");
		Account account = accountRepository.findById(java.util.UUID.fromString(player.accountId())).orElseThrow();
		account.setEnabled(false);
		accountRepository.saveAndFlush(account);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"disabled_user\",\"password\":\"" + AuthTestHelper.DEFAULT_PASSWORD + "\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message", equalTo("Invalid credentials")));
	}

	@Test
	void login_lockoutAfterRepeatedFailures() throws Exception {
		AuthTestHelper.registerPlayer(mockMvc, "lockout_user");
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"username\":\"lockout_user\",\"password\":\"WrongPassword-1\"}"))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"lockout_user\",\"password\":\"" + AuthTestHelper.DEFAULT_PASSWORD + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void missingJwtRejected() throws Exception {
		mockMvc.perform(get("/api/v1/players")).andExpect(status().isUnauthorized());
	}

	@Test
	void malformedJwtRejected() throws Exception {
		mockMvc.perform(get("/api/v1/players").header("Authorization", "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tamperedJwtRejected() throws Exception {
		RegisteredPlayer player = AuthTestHelper.registerPlayer(mockMvc, "tamper_user");
		String token = player.token().substring(0, player.token().length() - 2) + "XX";
		mockMvc.perform(get("/api/v1/players/" + player.playerId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

}