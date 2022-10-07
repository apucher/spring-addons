package com.c4_soft.springaddons.samples.webflux_jwtauthenticationtoken;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockBearerTokenAuthentication;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureWebTestClient
class SampleApiIntegrationTests {
	@Autowired
	WebTestClient api;

	@Test
	void greetWitoutAuthentication() throws Exception {
		api.get().uri("https://localhost/greet").exchange().expectStatus().isUnauthorized();
	}

	@Test
	@WithMockBearerTokenAuthentication()
	void greetWithDefaultMockAuthentication() throws Exception {
		api.get().uri("https://localhost/greet").exchange().expectBody(String.class).isEqualTo("Hello user! You are granted with [ROLE_USER].");
	}

	@Test
	@WithMockBearerTokenAuthentication(authorities = "ROLE_AUTHORIZED_PERSONNEL", attributes = @OpenIdClaims(preferredUsername = "Ch4mpy"))
	void greetJwtCh4mpy() throws Exception {
		api.get().uri("https://localhost/greet").exchange().expectBody(String.class)
				.isEqualTo("Hello Ch4mpy! You are granted with [ROLE_AUTHORIZED_PERSONNEL].");
	}

}
