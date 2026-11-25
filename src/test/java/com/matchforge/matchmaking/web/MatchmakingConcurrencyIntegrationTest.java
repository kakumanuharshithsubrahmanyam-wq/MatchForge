package com.matchforge.matchmaking.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.matchforge.matchmaking.domain.QueueEntryStatus;
import com.matchforge.matchmaking.repository.MatchRecordRepository;
import com.matchforge.matchmaking.repository.QueueEntryRepository;
import com.matchforge.auth.repository.AccountRepository;
import com.matchforge.player.domain.Player;
import com.matchforge.player.domain.PlayerStatus;
import com.matchforge.player.repository.PlayerRepository;
import com.matchforge.support.JwtTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchmakingConcurrencyIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlayerRepository playerRepository;

	@Autowired
	private QueueEntryRepository queueEntryRepository;

	@Autowired
	private MatchRecordRepository matchRecordRepository;

	@Autowired
	private AccountRepository accountRepository;

	@BeforeEach
	@Transactional
	void resetData() {
		queueEntryRepository.deleteAll();
		matchRecordRepository.deleteAll();
		accountRepository.findAll().stream()
				.filter(account -> account.getRole() == com.matchforge.auth.domain.AccountRole.PLAYER)
				.forEach(accountRepository::delete);
		playerRepository.deleteAll();
	}

	@Test
	void concurrentJoinBySamePlayerProducesSingleActiveEntry() throws Exception {
		Player player = persistAvailablePlayer("concurrent_join");

		try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
			List<Callable<Integer>> tasks = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				tasks.add(() -> {
					synchronized (mockMvc) {
						return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/matchmaking/queue")
										.with(JwtTestSupport.playerJwt(UUID.randomUUID(), player.getId()))
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"playerId\":\"" + player.getId() + "\"}"))
								.andReturn()
								.getResponse()
								.getStatus();
					}
				});
			}

			List<Future<Integer>> futures = executor.invokeAll(tasks);
			long accepted = futures.stream()
					.mapToInt(future -> {
						try {
							return future.get();
						} catch (Exception ex) {
							throw new IllegalStateException(ex);
						}
					})
					.filter(status -> status == 202)
					.count();
			long conflicts = futures.stream()
					.mapToInt(future -> {
						try {
							return future.get();
						} catch (Exception ex) {
							throw new IllegalStateException(ex);
						}
					})
					.filter(status -> status == 409)
					.count();

			assertThat(accepted).isEqualTo(1);
			assertThat(conflicts).isGreaterThanOrEqualTo(1);
			assertThat(queueEntryRepository.findAll().stream()
							.filter(entry -> entry.getStatus() == QueueEntryStatus.QUEUED)
							.count())
					.isEqualTo(1);
		}
	}

	@Test
	void multiplePlayersProduceSingleMatchEach() throws Exception {
		Player playerA = persistAvailablePlayer("concurrent_a");
		Player playerB = persistAvailablePlayer("concurrent_b");

		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/matchmaking/queue")
						.with(JwtTestSupport.playerJwt(UUID.randomUUID(), playerA.getId()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerA.getId() + "\"}"))
				.andReturn();

		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/matchmaking/queue")
						.with(JwtTestSupport.playerJwt(UUID.randomUUID(), playerB.getId()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"playerId\":\"" + playerB.getId() + "\"}"))
				.andReturn();

		assertThat(matchRecordRepository.count()).isEqualTo(1);
		assertThat(playerRepository.findById(playerA.getId()).orElseThrow().getStatus())
				.isEqualTo(PlayerStatus.MATCHED);
		assertThat(playerRepository.findById(playerB.getId()).orElseThrow().getStatus())
				.isEqualTo(PlayerStatus.MATCHED);
	}

	private Player persistAvailablePlayer(String username) {
		Player player = new Player();
		player.setUsername(username);
		player.setSkillRating(1000);
		player.setStatus(PlayerStatus.AVAILABLE);
		return playerRepository.saveAndFlush(player);
	}
}