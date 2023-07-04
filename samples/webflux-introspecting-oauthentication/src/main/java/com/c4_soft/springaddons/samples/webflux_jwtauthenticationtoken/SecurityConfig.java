package com.c4_soft.springaddons.samples.webflux_jwtauthenticationtoken;

import java.util.Collection;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenAuthenticationConverter;

import com.c4_soft.springaddons.security.oauth2.OAuthentication;
import com.c4_soft.springaddons.security.oauth2.OpenidClaimSet;
import com.c4_soft.springaddons.security.oauth2.config.SpringAddonsSecurityProperties;
import com.c4_soft.springaddons.security.oauth2.config.reactive.ResourceServerAuthorizeExchangeSpecPostProcessor;

import reactor.core.publisher.Mono;

@EnableReactiveMethodSecurity()
@Configuration
public class SecurityConfig {

	@Bean
	ReactiveOpaqueTokenAuthenticationConverter authenticationConverter(
			Converter<Map<String, Object>, Collection<? extends GrantedAuthority>> authoritiesConverter,
			SpringAddonsSecurityProperties addonsProperties) {
		return (
				String introspectedToken,
				OAuth2AuthenticatedPrincipal authenticatedPrincipal) -> Mono
						.just(
								new OAuthentication<>(
										new OpenidClaimSet(
												authenticatedPrincipal.getAttributes(),
												addonsProperties.getIssuerProperties(authenticatedPrincipal.getAttributes().get(JwtClaimNames.ISS))
														.getUsernameClaim()),
										authoritiesConverter.convert(authenticatedPrincipal.getAttributes()),
										introspectedToken));
	}

	@Bean
	ResourceServerAuthorizeExchangeSpecPostProcessor authorizeExchangeSpecPostProcessor() {
		// @formatter:off
		return (ServerHttpSecurity.AuthorizeExchangeSpec spec) -> spec
				.pathMatchers("/secured-route").hasRole("AUTHORIZED_PERSONNEL")
				.anyExchange().authenticated();
		// @formatter:on
	}

}