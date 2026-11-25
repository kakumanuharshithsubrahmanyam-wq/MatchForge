package com.matchforge.support;

import com.matchforge.auth.domain.AccountRole;
import com.matchforge.auth.repository.AccountRepository;
import com.matchforge.matchmaking.repository.MatchRecordRepository;
import com.matchforge.matchmaking.repository.QueueEntryRepository;
import com.matchforge.player.repository.PlayerRepository;
import com.matchforge.session.repository.GameSessionRepository;
import com.matchforge.session.repository.SessionParticipantRepository;

public final class IntegrationTestDatabaseCleaner {

	private IntegrationTestDatabaseCleaner() {
	}

	public static void resetGameplayData(
			SessionParticipantRepository participantRepository,
			GameSessionRepository gameSessionRepository,
			QueueEntryRepository queueEntryRepository,
			MatchRecordRepository matchRecordRepository,
			AccountRepository accountRepository,
			PlayerRepository playerRepository) {
		participantRepository.deleteAll();
		gameSessionRepository.deleteAll();
		queueEntryRepository.deleteAll();
		matchRecordRepository.deleteAll();
		accountRepository.findAll().stream()
				.filter(account -> account.getRole() == AccountRole.PLAYER)
				.forEach(accountRepository::delete);
		playerRepository.deleteAll();
	}
}