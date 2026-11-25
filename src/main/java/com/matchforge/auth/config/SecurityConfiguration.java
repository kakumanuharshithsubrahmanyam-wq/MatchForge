package com.matchforge.auth.config;

import javax.crypto.SecretKey;

import com.matchforge.auth.security.JwtTokenService;
import com.matchforge.common.config.ApiExposureProperties;
import com.matchforge.common.error.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityProperties.class, ApiExposureProperties.class})
public class SecurityConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	JwtDecoder jwtDecoder(JwtTokenService jwtTokenService) {
		return jwtTokenService.jwtDecoder();
	}

	private static final ObjectMapper ERROR_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ApiExposureProperties apiExposure) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> {
					auth.requestMatchers("/api/v1/auth/**").permitAll();
					auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
					if (apiExposure.isOpenApiEnabled() && apiExposure.isSwaggerUiEnabled()) {
						auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
					} else {
						auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("ADMIN");
					}
					auth.requestMatchers("/actuator/**").hasRole("ADMIN");
					auth.anyRequest().authenticated();
				})
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(
								(request, response, authException) -> writeError(request, response, 401, "Unauthorized", "Authentication required"))
						.accessDeniedHandler((request, response, accessDeniedException) ->
								writeError(request, response, 403, "Forbidden", "Access denied")));

		return http.build();
	}

	@Bean
	SecurityPropertiesValidator securityPropertiesValidator(SecurityProperties properties, Environment environment) {
		return new SecurityPropertiesValidator(properties, environment);
	}

	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter delegate = new JwtGrantedAuthoritiesConverter();
		delegate.setAuthoritiesClaimName("role");
		delegate.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(delegate);
		return converter;
	}

	private static void writeError(
			HttpServletRequest request,
			HttpServletResponse response,
			int status,
			String error,
			String message)
			throws java.io.IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		ErrorResponse body = ErrorResponse.of(status, error, message, request.getRequestURI());
		ERROR_MAPPER.writeValue(response.getOutputStream(), body);
	}

	static final class SecurityPropertiesValidator {
		SecurityPropertiesValidator(SecurityProperties properties, Environment environment) {
			String secret = properties.getJwtSecret();
			boolean production = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
			if (secret == null || secret.isBlank()) {
				if (production) {
					throw new IllegalStateException("JWT secret must be configured in production");
				}
			} else {
				JwtTokenService.secretKey(secret);
			}
			boolean adminUserSet = properties.getAdminUsername() != null && !properties.getAdminUsername().isBlank();
			boolean adminPassSet = properties.getAdminPassword() != null && !properties.getAdminPassword().isBlank();
			if (adminUserSet ^ adminPassSet) {
				throw new IllegalStateException("Both MATCHFORGE_ADMIN_USERNAME and MATCHFORGE_ADMIN_PASSWORD must be set");
			}
		}
	}
}