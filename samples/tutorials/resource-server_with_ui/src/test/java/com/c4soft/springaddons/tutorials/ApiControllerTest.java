package com.c4soft.springaddons.tutorials;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;

import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.c4_soft.springaddons.security.oauth2.test.mockmvc.AutoConfigureAddonsWebSecurity;
import com.c4_soft.springaddons.security.oauth2.test.mockmvc.MockMvcSupport;

@WebMvcTest(controllers = ApiController.class)
@AutoConfigureAddonsWebSecurity
@Import(WebSecurityConfig.class)
class ApiControllerTest {

	@Autowired
	MockMvcSupport mockMvc;

	@Test
	@WithMockJwtAuth(authorities = { "NICE", "AUTHOR" }, claims = @OpenIdClaims(preferredUsername = "Tonton Pirate"))
	void whenAuthenticatedThenCanGreet() throws Exception {
		mockMvc.get("/api/greet").andExpect(status().isOk()).andExpect(content().string("Hi Tonton Pirate! You are granted with: [NICE, AUTHOR]."));
	}

	@Test
	void whenAnonymousThenUnauthorized() throws Exception {
		mockMvc.get("/api/greet").andExpect(status().isUnauthorized());
	}

}
