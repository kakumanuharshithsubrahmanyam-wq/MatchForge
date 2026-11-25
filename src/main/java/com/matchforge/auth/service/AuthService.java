package com.matchforge.auth.service;

import com.matchforge.auth.config.SecurityProperties;
import com.matchforge.auth.domain.Account;
import com.matchforge.auth.domain.AccountRole;
import com.matchforge.auth.dto.AuthResponse;
import com.matchforge.auth.dto.LoginRequest;
import com.matchforge.auth.dto.RegisterRequest;
import com.matchforge.auth.repository.AccountRepository;
import com.matchforge.auth.security.JwtTokenService;
import com.matchforge.common.error.ConflictException;
import com.matchforge.player.dto.CreatePlayerRequest;
import com.matchforge.player.dto.PlayerResponse;
import com.matchforge.common.observability.MatchForgeMetrics;
import com.matchforge.player.service.PlayerService;
import com.matchforge.player.service.UsernameNormalizer;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final AccountRepository accountRepository;
	private final PlayerService playerService;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;
	private final SecurityProperties securityProperties;
	private final MatchForgeMetrics metrics;
	private final Clock clock;

	public AuthService(
			AccountRepository accountRepository,
			PlayerService playerService,
			PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService,
			SecurityProperties securityProperties,
			MatchForgeMetrics metrics,
			Clock clock) {
		this.accountRepository = accountRepository;
		this.playerService = playerService;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
		this.securityProperties = securityProperties;
		this.metrics = metrics;
		this.clock = clock;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String username = UsernameNormalizer.normalize(request.username());
		PasswordPolicy.validate(request.password());

		if (accountRepository.existsByUsername(username)) {
			throw new ConflictException("Username already taken: " + username);
		}

		PlayerResponse player = playerService.register(new CreatePlayerRequest(username, request.skillRating()));
		Account account = new Account();
		account.setUsername(username);
		account.setPasswordHash(passwordEncoder.encode(request.password()));
		account.setRole(AccountRole.PLAYER);
		account.setPlayerId(player.id());
		account.setEnabled(true);
		accountRepository.save(account);
		metrics.playerRegistered();
		log.info("Player account registered accountId={} playerId={}", account.getId(), player.id());

		return AuthResponse.bearer(jwtTokenService.createToken(account), securityProperties.getJwtExpirationSeconds(), player.id(), account);
	}

	@Transactional
	public AuthResponse login(LoginRequest request) {
		String username = UsernameNormalizer.normalize(request.username());
		Instant now = clock.instant();
		Account account = accountRepository.findByUsernameForUpdate(username).orElse(null);

		if (account == null) {
			metrics.authFailure();
			throw new BadCredentialsException("Invalid credentials");
		}

		if (!account.isEnabled() || account.isLoginLocked(now)) {
			metrics.authFailure();
			throw new BadCredentialsException("Invalid credentials");
		}

		if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
			recordFailedLogin(account, now);
			metrics.authFailure();
			throw new BadCredentialsException("Invalid credentials");
		}

		account.setFailedLoginAttempts(0);
		account.setLockedUntil(null);
		accountRepository.save(account);

		return AuthResponse.bearer(
				jwtTokenService.createToken(account),
				securityProperties.getJwtExpirationSeconds(),
				account.getPlayerId(),
				account);
	}

	private void recordFailedLogin(Account account, Instant now) {
		int attempts = account.getFailedLoginAttempts() + 1;
		account.setFailedLoginAttempts(attempts);
		if (attempts >= securityProperties.getMaxFailedLoginAttempts()) {
			account.setLockedUntil(now.plusSeconds(securityProperties.getLoginLockoutSeconds()));
			log.warn("Account locked due to repeated failed logins accountId={}", account.getId());
		}
		accountRepository.save(account);
	}
}