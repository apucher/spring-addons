package com.c4soft.springaddons.security.oauth2.server.resource.authentication;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.util.StringUtils;

import com.c4soft.oauth2.rfc7519.JwtClaimSet;

public class JwtOAuth2ClaimSetAuthenticationManager<T extends JwtClaimSet> implements AuthenticationManager {
	private final JwtDecoder jwtDecoder;
	private final Converter<Map<String, Object>, T> claimsConverter;
	private final Converter<T, Collection<GrantedAuthority>> authoritiesConverter;
	private final Set<String> requiredScopes;

	public JwtOAuth2ClaimSetAuthenticationManager(
			JwtDecoder jwtDecoder,
			Converter<Map<String, Object>, T> claimsConverter,
			Converter<T, Collection<GrantedAuthority>> authoritiesConverter,
			Set<String> requiredScopes) {
		this.jwtDecoder = jwtDecoder;
		this.claimsConverter = claimsConverter;
		this.authoritiesConverter = authoritiesConverter;
		this.requiredScopes = requiredScopes;
	}

	@Override
	public OAuth2ClaimSetAuthentication<T> authenticate(Authentication authentication) throws AuthenticationException {
		if (!(authentication instanceof BearerTokenAuthenticationToken)) {
			return null;
		}
		BearerTokenAuthenticationToken bearer = (BearerTokenAuthenticationToken) authentication;

		try {
			final Jwt jwt = jwtDecoder.decode(bearer.getToken());
			final T claims = claimsConverter.convert(jwt.getClaims());
			final Set<String> scopes = claims.containsKey("scope") ? claims.getAsStringSet("scope") : claims.getAsStringSet("scp");
			if(scopes == null) {
				throw new OAuth2AuthenticationException(invalidToken("Token has no scope claim. It is required."));
			}
			if(!scopes.containsAll(requiredScopes)) {
				final String msg = String.format("%s scopes are required but token is granted with %s", requiredScopes, scopes);
				throw new OAuth2AuthenticationException(invalidToken(msg));
			}
			return new OAuth2ClaimSetAuthentication<>(claims, authoritiesConverter);
		} catch (OAuth2IntrospectionException failed) {
			throw new OAuth2AuthenticationException(invalidToken(failed.getMessage()));
		}
	}

	private static BearerTokenError invalidToken(String message) {
		final String msg = StringUtils.hasLength(message) ? message
				: "An error occurred while attempting to introspect the token: Invalid token";

		return new BearerTokenError("invalid_token", HttpStatus.UNAUTHORIZED, msg,
				"https://tools.ietf.org/html/rfc7662#section-2.2");
	}

}