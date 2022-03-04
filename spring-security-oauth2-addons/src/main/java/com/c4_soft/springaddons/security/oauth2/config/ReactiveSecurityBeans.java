package com.c4_soft.springaddons.security.oauth2.config;

import java.nio.charset.Charset;
import java.util.Arrays;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;

import com.c4_soft.springaddons.security.oauth2.ReactiveJwt2AuthenticationConverter;
import com.c4_soft.springaddons.security.oauth2.ReactiveJwt2GrantedAuthoritiesConverter;
import com.c4_soft.springaddons.security.oauth2.ReactiveJwt2OidcTokenConverter;
import com.c4_soft.springaddons.security.oauth2.config.SpringAddonsSecurityProperties.CorsProperties;
import com.c4_soft.springaddons.security.oauth2.oidc.OidcAuthentication;
import com.c4_soft.springaddons.security.oauth2.oidc.OidcToken;
import com.c4_soft.springaddons.security.oauth2.oidc.ReactiveJwt2OidcAuthenticationConverter;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ReactiveSecurityBeans {
	private final OAuth2ResourceServerProperties auth2ResourceServerProperties;
	private final SpringAddonsSecurityProperties securityProperties;

	@ConditionalOnMissingBean
	@Bean
	public <T extends OidcToken> ReactiveJwt2AuthenticationConverter<OidcAuthentication<T>> authenticationConverter(
			ReactiveJwt2GrantedAuthoritiesConverter authoritiesConverter,
			ReactiveJwt2OidcTokenConverter<T> tokenConverter) {
		return new ReactiveJwt2OidcAuthenticationConverter<>(authoritiesConverter, tokenConverter);
	}

	@ConditionalOnMissingBean
	@Bean
	public ReactiveJwt2GrantedAuthoritiesConverter authoritiesConverter() {
		return new ReactiveEmbeddedJwt2GrantedAuthoritiesConverter(securityProperties);
	}

	@ConditionalOnMissingBean
	@Bean
	public ReactiveJwt2OidcTokenConverter<OidcToken> tokenConverter() {
		return (Jwt jwt) -> Mono.just(new OidcToken(jwt.getClaims()));
	}

	@ConditionalOnMissingBean
	@Bean
	public ReactiveJwtDecoder jwtDecoder() {
		return ReactiveJwtDecoders.fromOidcIssuerLocation(auth2ResourceServerProperties.getJwt().getIssuerUri());
	}

	@ConditionalOnMissingBean
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		for (final CorsProperties corsProps : securityProperties.getCors()) {
			final CorsConfiguration configuration = new CorsConfiguration();
			configuration.setAllowedOrigins(Arrays.asList(corsProps.getAllowedOrigins()));
			configuration.setAllowedMethods(Arrays.asList(corsProps.getAllowedMethods()));
			configuration.setAllowedHeaders(Arrays.asList(corsProps.getAllowedHeaders()));
			configuration.setExposedHeaders(Arrays.asList(corsProps.getExposedHeaders()));
			source.registerCorsConfiguration(corsProps.getPath(), configuration);
		}
		return source;
	}

	@ConditionalOnMissingBean
	@Bean
	public ServerAccessDeniedHandler serverAccessDeniedHandler() {
		return (ServerWebExchange exchange, AccessDeniedException ex) -> exchange.getPrincipal().flatMap(principal -> {
			final ServerHttpResponse response = exchange.getResponse();
			response.setStatusCode(principal instanceof AnonymousAuthenticationToken ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN);
			response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
			final DataBufferFactory dataBufferFactory = response.bufferFactory();
			final DataBuffer buffer = dataBufferFactory.wrap(ex.getMessage().getBytes(Charset.defaultCharset()));
			return response.writeWith(Mono.just(buffer)).doOnError(error -> DataBufferUtils.release(buffer));
		});
	}
}