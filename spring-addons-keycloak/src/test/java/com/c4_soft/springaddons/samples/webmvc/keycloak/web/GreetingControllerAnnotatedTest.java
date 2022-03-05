package com.c4_soft.springaddons.samples.webmvc.keycloak.web;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import com.c4_soft.springaddons.samples.webmvc.keycloak.KeycloakSpringBootSampleApp;
import com.c4_soft.springaddons.samples.webmvc.keycloak.service.MessageService;
import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.JsonObjectClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.keycloak.KeycloakAccess;
import com.c4_soft.springaddons.security.oauth2.test.annotations.keycloak.KeycloakAccessToken;
import com.c4_soft.springaddons.security.oauth2.test.annotations.keycloak.KeycloakAuthorization;
import com.c4_soft.springaddons.security.oauth2.test.annotations.keycloak.KeycloakPermission;
import com.c4_soft.springaddons.security.oauth2.test.annotations.keycloak.KeycloakResourceAccess;
import com.c4_soft.springaddons.security.oauth2.test.annotations.keycloak.WithMockKeycloakAuth;
import com.c4_soft.springaddons.security.oauth2.test.mockmvc.keycloak.ServletKeycloakAuthUnitTestingSupport;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = GreetingController.class)
@Import({ ServletKeycloakAuthUnitTestingSupport.UnitTestConfig.class, KeycloakSpringBootSampleApp.KeycloakConfig.class })
public class GreetingControllerAnnotatedTest {
	private static final String GREETING = "Hello %s! You are granted with %s.";

	@MockBean
	MessageService messageService;

	@MockBean
	JwtDecoder jwtDecoder;

	@Autowired
	MockMvc api;

	@Before
	public void setUp() {
		when(messageService.greet(any())).thenAnswer(invocation -> {
			final Authentication auth = invocation.getArgument(0, Authentication.class);
			return String.format(GREETING, auth.getName(), auth.getAuthorities());
		});
	}

	@Test
	@WithMockKeycloakAuth
	public void whenAuthenticatedWithoutAuthorizedPersonnelThenSecuredRouteIsForbidden() throws Exception {
		api.perform(get("/secured-route")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockKeycloakAuth({ "AUTHORIZED_PERSONNEL" })
	public void whenAuthenticatedWithAuthorizedPersonnelThenSecuredRouteIsOk() throws Exception {
		api.perform(get("/secured-route")).andExpect(status().isOk());
	}

	// @formatter:off
	@Test
	@WithMockKeycloakAuth(
			authorities = {"USER", "AUTHORIZED_PERSONNEL" },
			claims = @OpenIdClaims(
					sub = "42",
					jti = "123-456-789",
					nbf = "2020-11-18T20:38:00Z",
					sessionState = "987-654-321",
					email = "ch4mp@c4-soft.com",
					emailVerified = true,
					nickName = "Tonton-Pirate",
					preferredUsername = "ch4mpy",
					otherClaims = @Claims(jsonObjectClaims = @JsonObjectClaim(name = "foo", value = OTHER_CLAIMS))),
			accessToken = @KeycloakAccessToken(
					realmAccess = @KeycloakAccess(roles = { "TESTER" }),
					authorization = @KeycloakAuthorization(permissions = @KeycloakPermission(rsid = "toto", rsname = "truc", scopes = "abracadabra")),
					resourceAccess = {
							@KeycloakResourceAccess(resourceId = "resourceA", access = @KeycloakAccess(roles = {"A_TESTER"})),
							@KeycloakResourceAccess(resourceId = "resourceB", access = @KeycloakAccess(roles = {"B_TESTER"}))}))
	// @formatter:on
	public void whenAuthenticatedWithKeycloakAuthenticationTokenThenCanGreet() throws Exception {
		api
				.perform(get("/greet"))
				.andExpect(status().isOk())
				.andExpect(content().string(startsWith("Hello ch4mpy! You are granted with ")))
				.andExpect(content().string(containsString("AUTHORIZED_PERSONNEL")))
				.andExpect(content().string(containsString("USER")))
				.andExpect(content().string(containsString("TESTER")))
				.andExpect(content().string(containsString("A_TESTER")))
				.andExpect(content().string(containsString("B_TESTER")));
	}

	@Test
	@WithMockKeycloakAuth
	public void testAuthentication() throws Exception {
		api.perform(get("/authentication")).andExpect(status().isOk()).andExpect(content().string("Hello user"));
	}

	@Test
	@WithMockKeycloakAuth
	public void testPrincipal() throws Exception {
		api.perform(get("/principal")).andExpect(status().isOk()).andExpect(content().string("Hello user"));
	}

	static final String OTHER_CLAIMS = "{\"bar\":\"bad\", \"nested\":{\"deep\":\"her\"}, \"arr\":[1,2,3]}";
}
