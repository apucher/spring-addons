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

import com.c4_soft.springaddons.security.oidc.OAuthentication;
import com.c4_soft.springaddons.security.oidc.OpenidClaimSet;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import com.c4_soft.springaddons.security.oidc.starter.reactive.resourceserver.ResourceServerAuthorizeExchangeSpecPostProcessor;

import reactor.core.publisher.Mono;

@EnableReactiveMethodSecurity()
@Configuration
public class SecurityConfig {

	@Bean
	ReactiveOpaqueTokenAuthenticationConverter authenticationConverter(
			Converter<Map<String, Object>, Collection<? extends GrantedAuthority>> authoritiesConverter,
			SpringAddonsOidcProperties addonsProperties) {
		return (
				String introspectedToken,
				OAuth2AuthenticatedPrincipal authenticatedPrincipal) -> Mono
						.just(
								new OAuthentication<>(
										new OpenidClaimSet(
												authenticatedPrincipal.getAttributes(),
												addonsProperties.getOpProperties(authenticatedPrincipal.getAttributes().get(JwtClaimNames.ISS))
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