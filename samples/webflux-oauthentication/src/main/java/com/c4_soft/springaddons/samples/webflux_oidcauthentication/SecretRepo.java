package com.c4_soft.springaddons.samples.webflux_oidcauthentication;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Mono;

@Repository
public class SecretRepo {
	@PreAuthorize("authentication.attributes['preferred_username'] eq #username")
	public Mono<String> findSecretByUsername(String username) {
		return Mono.just("Don't ever tell it");
	}
}