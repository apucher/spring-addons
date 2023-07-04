package com.c4_soft.springaddons.security.oauth2.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

import com.c4_soft.springaddons.security.oauth2.OpenidClaimSet;
import com.c4_soft.springaddons.security.oauth2.config.SpringAddonsSecurityProperties.Case;
import com.c4_soft.springaddons.security.oauth2.config.SpringAddonsSecurityProperties.IssuerProperties;
import com.c4_soft.springaddons.security.oauth2.config.SpringAddonsSecurityProperties.SimpleAuthoritiesMappingProperties;

public class ConfigurableJwtGrantedAuthoritiesConverterTest {

	@Test
	public void test() throws URISyntaxException {
		final var issuer = new URI("https://authorisation-server");

		final var client1Roles = List.of("R11", "r12");

		final var client2Roles = List.of("R21", "r22");

		final var client3Roles = List.of("R31", "r32");

		final var realmRoles = List.of("r1", "r2");

		// @formatter:off
		final var claims = Map.of(
				JwtClaimNames.ISS, issuer,
				"resource_access", Map.of(
						"client1", Map.of("roles", client1Roles),
						"client2", Map.of("roles", client2Roles.stream().collect(Collectors.joining(", "))),
						"client3", Map.of("roles", client3Roles.stream().collect(Collectors.joining(" ")))),
				"realm_access", Map.of("roles", realmRoles));
		// @formatter:on

		final var now = Instant.now();
		final var jwt = new Jwt("a.b.C", now, Instant.ofEpochSecond(now.getEpochSecond() + 3600), Map.of("machin", "truc"), claims);

		final var issuerProperties = new IssuerProperties();
		issuerProperties.setLocation(issuer);

		final var properties = new SpringAddonsSecurityProperties();
		properties.setIssuers(new IssuerProperties[] { issuerProperties });

		final var converter = new ConfigurableClaimSetAuthoritiesConverter(properties);
		final var claimSet = new OpenidClaimSet(jwt.getClaims());

		// Assert mapping with default properties
		assertThat(converter.convert(claimSet).stream().map(GrantedAuthority::getAuthority).toList()).containsExactlyInAnyOrder("r1", "r2");

		// Assert with prefix & uppercase
		issuerProperties.setAuthorities(
				new SimpleAuthoritiesMappingProperties[] {
						new SimpleAuthoritiesMappingProperties("$.realm_access.roles", "MACHIN_", Case.UNCHANGED),
						new SimpleAuthoritiesMappingProperties("resource_access.client1.roles", "TRUC_", Case.LOWER),
						new SimpleAuthoritiesMappingProperties("resource_access.client3.roles", "CHOSE_", Case.UPPER) });

		assertThat(converter.convert(claimSet).stream().map(GrantedAuthority::getAuthority).toList())
				.containsExactlyInAnyOrder("TRUC_r11", "TRUC_r12", "CHOSE_R31", "CHOSE_R32", "MACHIN_r1", "MACHIN_r2");
	}

}
