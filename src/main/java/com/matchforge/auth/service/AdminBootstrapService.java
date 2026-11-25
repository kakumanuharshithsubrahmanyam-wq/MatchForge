package com.matchforge.auth.service;

import com.matchforge.auth.config.SecurityProperties;
import com.matchforge.auth.domain.Account;
import com.matchforge.auth.domain.AccountRole;
import com.matchforge.auth.repository.AccountRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.matchforge.player.service.UsernameNormalizer;

@Component
public class AdminBootstrapService implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

	private final SecurityProperties securityProperties;
	private final AccountRepository accountRepository;
	private final PasswordEncoder passwordEncoder;

	public AdminBootstrapService(
			SecurityProperties securityProperties, AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
		this.securityProperties = securityProperties;
		this.accountRepository = accountRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		String username = securityProperties.getAdminUsername();
		String password = securityProperties.getAdminPassword();
		if (username == null || username.isBlank()) {
			return;
		}
		String normalized = UsernameNormalizer.normalize(username);
		if (accountRepository.findByUsername(normalized).isPresent()) {
			return;
		}
		Account admin = new Account();
		admin.setUsername(normalized);
		admin.setPasswordHash(passwordEncoder.encode(password));
		admin.setRole(AccountRole.ADMIN);
		admin.setEnabled(true);
		accountRepository.save(admin);
		log.info("Bootstrapped administrator account");
	}
}