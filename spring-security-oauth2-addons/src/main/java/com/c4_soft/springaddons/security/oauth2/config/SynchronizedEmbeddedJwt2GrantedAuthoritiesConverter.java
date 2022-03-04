package com.c4_soft.springaddons.security.oauth2.config;

import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.c4_soft.springaddons.security.oauth2.SynchronizedJwt2GrantedAuthoritiesConverter;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SynchronizedEmbeddedJwt2GrantedAuthoritiesConverter implements SynchronizedJwt2GrantedAuthoritiesConverter {
	private final SpringAddonsSecurityProperties securityProperties;

	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		return securityProperties.getAuthorities(jwt.getClaims()).collect(Collectors.toList());
	}

}