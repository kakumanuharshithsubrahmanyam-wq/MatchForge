package com.matchforge.player.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.matchforge.player.dto.CreatePlayerRequest;
import com.matchforge.player.dto.PlayerResponse;
import com.matchforge.player.dto.SetAvailabilityRequest;
import com.matchforge.player.dto.UpdatePlayerRequest;
import com.matchforge.player.service.PlayerService;

import jakarta.validation.Valid;

import com.matchforge.auth.security.SecurityContextSupport;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/players")
@Tag(name = "Players", description = "Player profiles and availability")
@SecurityRequirement(name = "bearerAuth")
public class PlayerController {

	private final PlayerService playerService;

	public PlayerController(PlayerService playerService) {
		this.playerService = playerService;
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<PlayerResponse> register(@Valid @RequestBody CreatePlayerRequest request) {
		PlayerResponse created = playerService.register(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{playerId}")
				.buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping("/{playerId}")
	@PreAuthorize("@authz.canAccessPlayer(#playerId)")
	public PlayerResponse getById(@PathVariable UUID playerId) {
		return playerService.getById(playerId);
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public List<PlayerResponse> listPlayers() {
		return playerService.listPlayers();
	}

	@PatchMapping("/{playerId}")
	@PreAuthorize("@authz.canAccessPlayer(#playerId)")
	public PlayerResponse updateProfile(@PathVariable UUID playerId, @Valid @RequestBody UpdatePlayerRequest request) {
		return playerService.updateProfile(playerId, request);
	}

	@PutMapping("/{playerId}/availability")
	@PreAuthorize("@authz.canAccessPlayer(#playerId)")
	public PlayerResponse setAvailability(
			@PathVariable UUID playerId, @Valid @RequestBody SetAvailabilityRequest request) {
		return playerService.setAvailability(playerId, request);
	}
}