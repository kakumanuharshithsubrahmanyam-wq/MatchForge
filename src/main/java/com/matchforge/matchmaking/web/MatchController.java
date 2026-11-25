package com.matchforge.matchmaking.web;

import java.util.UUID;

import com.matchforge.matchmaking.dto.MatchResponse;
import com.matchforge.matchmaking.service.MatchmakingService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/matches")
@Tag(name = "Matches", description = "Match details for participants or ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class MatchController {

	private final MatchmakingService matchmakingService;

	public MatchController(MatchmakingService matchmakingService) {
		this.matchmakingService = matchmakingService;
	}

	@GetMapping("/{matchId}")
	@PreAuthorize("@authz.canViewMatch(#matchId)")
	public MatchResponse getMatch(@PathVariable UUID matchId) {
		return matchmakingService.getMatch(matchId);
	}
}