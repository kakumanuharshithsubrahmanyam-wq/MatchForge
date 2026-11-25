package com.matchforge.matchmaking.web;

import java.util.UUID;

import com.matchforge.auth.security.SecurityContextSupport;
import com.matchforge.matchmaking.dto.JoinQueueRequest;
import com.matchforge.matchmaking.dto.QueueStatusResponse;
import com.matchforge.matchmaking.service.MatchmakingService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/matchmaking")
@Tag(name = "Matchmaking", description = "Queue operations (PLAYER owns playerId)")
@SecurityRequirement(name = "bearerAuth")
public class MatchmakingController {

	private final MatchmakingService matchmakingService;

	public MatchmakingController(MatchmakingService matchmakingService) {
		this.matchmakingService = matchmakingService;
	}

	@PostMapping("/queue")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@PreAuthorize("hasRole('PLAYER')")
	public QueueStatusResponse joinQueue(@Valid @RequestBody JoinQueueRequest request) {
		SecurityContextSupport.requireSelfOrAdmin(request.playerId());
		return matchmakingService.joinQueue(request.playerId());
	}

	@DeleteMapping("/queue/{playerId}")
	@ResponseStatus(HttpStatus.OK)
	@PreAuthorize("@authz.canAccessPlayer(#playerId)")
	public QueueStatusResponse leaveQueue(@PathVariable UUID playerId) {
		return matchmakingService.leaveQueue(playerId);
	}

	@GetMapping("/queue/{playerId}")
	@PreAuthorize("@authz.canAccessPlayer(#playerId)")
	public QueueStatusResponse getQueueStatus(@PathVariable UUID playerId) {
		return matchmakingService.getQueueStatus(playerId);
	}
}