package com.matchforge.session.web;

import java.util.List;
import java.util.UUID;

import com.matchforge.session.dto.PlayerSessionRequest;
import com.matchforge.session.dto.SessionParticipantResponse;
import com.matchforge.session.dto.SessionResponse;
import com.matchforge.session.service.SessionService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "Session lifecycle; complete/cancel require ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

	private final SessionService sessionService;

	public SessionController(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@GetMapping("/{sessionId}")
	@PreAuthorize("@authz.canViewSession(#sessionId)")
	public SessionResponse getSession(@PathVariable UUID sessionId) {
		return sessionService.getSession(sessionId);
	}

	@GetMapping("/{sessionId}/participants")
	@PreAuthorize("@authz.canViewSession(#sessionId)")
	public List<SessionParticipantResponse> listParticipants(@PathVariable UUID sessionId) {
		return sessionService.listParticipants(sessionId);
	}

	@PostMapping("/{sessionId}/join")
	@PreAuthorize("@authz.canAccessSessionParticipant(#sessionId, #request.playerId())")
	public SessionResponse join(@PathVariable UUID sessionId, @Valid @RequestBody PlayerSessionRequest request) {
		return sessionService.joinSession(sessionId, request.playerId());
	}

	@PostMapping("/{sessionId}/heartbeat")
	@PreAuthorize("@authz.canAccessSessionParticipant(#sessionId, #request.playerId())")
	public SessionResponse heartbeat(@PathVariable UUID sessionId, @Valid @RequestBody PlayerSessionRequest request) {
		return sessionService.heartbeat(sessionId, request.playerId());
	}

	@PostMapping("/{sessionId}/disconnect")
	@PreAuthorize("@authz.canAccessSessionParticipant(#sessionId, #request.playerId())")
	public SessionResponse disconnect(
			@PathVariable UUID sessionId, @Valid @RequestBody PlayerSessionRequest request) {
		return sessionService.disconnect(sessionId, request.playerId());
	}

	@PostMapping("/{sessionId}/reconnect")
	@PreAuthorize("@authz.canAccessSessionParticipant(#sessionId, #request.playerId())")
	public SessionResponse reconnect(
			@PathVariable UUID sessionId, @Valid @RequestBody PlayerSessionRequest request) {
		return sessionService.reconnect(sessionId, request.playerId());
	}

	@PostMapping("/{sessionId}/complete")
	@PreAuthorize("hasRole('ADMIN')")
	public SessionResponse complete(@PathVariable UUID sessionId) {
		return sessionService.completeSession(sessionId);
	}

	@PostMapping("/{sessionId}/cancel")
	@PreAuthorize("hasRole('ADMIN')")
	public SessionResponse cancel(@PathVariable UUID sessionId) {
		return sessionService.cancelSession(sessionId);
	}
}