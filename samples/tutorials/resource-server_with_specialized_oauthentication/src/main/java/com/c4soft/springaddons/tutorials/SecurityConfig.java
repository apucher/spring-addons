package com.c4soft.springaddons.tutorials;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.GrantedAuthority;

import com.c4_soft.springaddons.security.oauth2.config.synchronised.OAuth2AuthenticationFactory;
import com.c4_soft.springaddons.security.oauth2.spring.C4MethodSecurityExpressionHandler;
import com.c4_soft.springaddons.security.oauth2.spring.C4MethodSecurityExpressionRoot;

@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	@Bean
	OAuth2AuthenticationFactory authenticationFactory(Converter<Map<String, Object>, Collection<? extends GrantedAuthority>> authoritiesConverter) {
		return (bearerString, claims) -> {
			final ProxiesClaimSet claimSet = new ProxiesClaimSet(claims);
			return new ProxiesAuthentication(claimSet, authoritiesConverter.convert(claimSet), bearerString);
		};
	}

	@Bean
	MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
		return new C4MethodSecurityExpressionHandler(ProxiesMethodSecurityExpressionRoot::new);
	}

	static final class ProxiesMethodSecurityExpressionRoot extends C4MethodSecurityExpressionRoot {

		public boolean is(String preferredUsername) {
			return Objects.equals(preferredUsername, getAuthentication().getName());
		}

		public Proxy onBehalfOf(String proxiedUsername) {
			return get(ProxiesAuthentication.class).map(a -> a.getProxyFor(proxiedUsername))
					.orElse(new Proxy(proxiedUsername, getAuthentication().getName(), Arrays.asList()));
		}

		public boolean isNice() {
			return hasAnyAuthority("NICE", "SUPER_COOL");
		}
	}
}