package com.matchforge.auth.service;

import java.util.UUID;

import com.matchforge.auth.security.SecurityContextSupport;
import com.matchforge.matchmaking.repository.MatchRecordRepository;
import com.matchforge.session.repository.SessionParticipantRepository;

import org.springframework.stereotype.Service;

@Service("authz")
public class AuthorizationService {

	private final MatchRecordRepository matchRecordRepository;
	private final SessionParticipantRepository sessionParticipantRepository;

	public AuthorizationService(
			MatchRecordRepository matchRecordRepository, SessionParticipantRepository sessionParticipantRepository) {
		this.matchRecordRepository = matchRecordRepository;
		this.sessionParticipantRepository = sessionParticipantRepository;
	}

	public boolean canAccessPlayer(UUID playerId) {
		return SecurityContextSupport.currentPrincipal()
				.map(principal -> principal.role() == com.matchforge.auth.domain.AccountRole.ADMIN
						|| playerId.equals(principal.playerId()))
				.orElse(false);
	}

	public boolean canViewMatch(UUID matchId) {
		if (SecurityContextSupport.isAdmin()) {
			return true;
		}
		UUID playerId = SecurityContextSupport.requirePlayerId();
		return matchRecordRepository
				.findById(matchId)
				.map(match -> match.getPlayerOneId().equals(playerId) || match.getPlayerTwoId().equals(playerId))
				.orElse(false);
	}

	public boolean canViewSession(UUID sessionId) {
		if (SecurityContextSupport.isAdmin()) {
			return true;
		}
		UUID playerId = SecurityContextSupport.requirePlayerId();
		return sessionParticipantRepository.findBySessionId(sessionId).stream()
				.anyMatch(participant -> participant.getPlayerId().equals(playerId));
	}

	public boolean canAccessSessionParticipant(UUID sessionId, UUID playerId) {
		if (SecurityContextSupport.isAdmin()) {
			return true;
		}
		SecurityContextSupport.requireSelfOrAdmin(playerId);
		return sessionParticipantRepository
				.findBySessionIdAndPlayerId(sessionId, playerId)
				.isPresent();
	}
}