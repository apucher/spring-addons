package com.c4_soft.springaddons.samples.webflux_oidcauthentication;

import java.util.Collection;
import java.util.Map;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;

import com.c4_soft.springaddons.security.oauth2.OAuthentication;
import com.c4_soft.springaddons.security.oauth2.OpenidClaimSet;
import com.c4_soft.springaddons.security.oauth2.config.reactive.AuthorizeExchangeSpecPostProcessor;
import com.c4_soft.springaddons.security.oauth2.config.reactive.OAuth2AuthenticationFactory;

import reactor.core.publisher.Mono;

@SpringBootApplication
public class SampleApi {
	public static void main(String[] args) {
		new SpringApplicationBuilder(SampleApi.class).web(WebApplicationType.REACTIVE).run(args);
	}

	@EnableReactiveMethodSecurity()
	public static class SecurityConfig {

		@Bean
		OAuth2AuthenticationFactory authenticationFactory(Converter<Map<String, Object>, Collection<? extends GrantedAuthority>> authoritiesConverter) {
			return (bearerString, claims) -> Mono.just(new OAuthentication<>(new OpenidClaimSet(claims), authoritiesConverter.convert(claims), bearerString));
		}

		@Bean
		public AuthorizeExchangeSpecPostProcessor authorizeExchangeSpecPostProcessor() {
			// @formatter:off
			return (ServerHttpSecurity.AuthorizeExchangeSpec spec) -> spec
					.pathMatchers("/secured-route").hasRole("AUTHORIZED_PERSONNEL")
					.anyExchange().authenticated();
			// @formatter:on
		}

	}
}
