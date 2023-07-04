package com.c4_soft.springaddons.security.oauth2.config;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>
 * Would better be named "SpringAddonsOAuth2ResourceServerProperties" and use "com.c4-soft.springaddons.security.resource-server" as prefix to better
 * distinguish it from {@link SpringAddonsOAuth2ClientProperties}. But the later was created later and keeping this name and prefix prevents from anoying
 * breaking changes.
 * </p>
 * Here are defaults:
 *
 * <pre>
 * com.c4-soft.springaddons.security.issuers[0].location=https://localhost:8443/realms/master
 * com.c4-soft.springaddons.security.issuers[0].authorities[0].path=realm_access.roles
 * com.c4-soft.springaddons.security.issuers[0].authorities[0].prefix=
 * com.c4-soft.springaddons.security.issuers[0].authorities[0].caze=UNCHANGED
 * com.c4-soft.springaddons.security.statless-sessions=true
 * com.c4-soft.springaddons.security.csrf-enabled=true
 * com.c4-soft.springaddons.security.permit-all=
 * com.c4-soft.springaddons.security.redirect-to-login-if-unauthorized-on-restricted-content=true
 * </pre>
 *
 * Default conf for CORS being an empty array, CORS is disabled. To enable it (following is very permissive, define something more restrictive):
 *
 * <pre>
 * com.c4-soft.springaddons.security.cors[0].path=/**
 * </pre>
 *
 * @author ch4mp
 */
@Data
@AutoConfiguration
@ConfigurationProperties(prefix = "com.c4-soft.springaddons.security")
public class SpringAddonsSecurityProperties {
	/**
	 * If false, all resource-server web auto-configuration is disabled (only the authorities converter {@link ConditionalOnMissingBean} is provided)
	 */
	private boolean enabled = true;

	/**
	 * issuer URI, JWK set URI as well as authorities mapping configuration for each issuer. A minimum of one issuer must be defined.
	 */
	@NestedConfigurationProperty
	private IssuerProperties[] issuers = {};

	/**
	 * Fine grained CORS configuration per path. If left empty, CORS is disabled.
	 */
	@NestedConfigurationProperty
	private CorsProperties[] cors = {};

	/**
	 * Path matchers for resources accessible to anonymous
	 */
	private String[] permitAll = {};

	/**
	 * Whether to return "302 redirect to login" or "401 unauthorized" for requests with missing or invalid authorization. It should remain false, manipulate
	 * with caution.
	 */
	private boolean redirectToLoginIfUnauthorizedOnRestrictedContent = false;

	/**
	 * Whether to disable sessions. It should remain true.
	 */
	private boolean statlessSessions = true;

	/**
	 * How to configure CSRF protection. Default is disabled CSRF protection if sessions are disabled and session based protection if sessions are enabled.
	 */
	private Csrf csrf = Csrf.DEFAULT;

	/**
	 * @param  iss                                              the issuer URI string
	 * @return                                                  configuration properties associated with the provided issuer URI
	 * @throws MissingAuthorizationServerConfigurationException if configuration properties don not have an entry for the exact issuer (even trailing slash is
	 *                                                          important)
	 */
	public IssuerProperties getIssuerProperties(String iss) throws MissingAuthorizationServerConfigurationException {
		return Stream.of(issuers).filter(issuerProps -> Objects.equals(Optional.ofNullable(issuerProps.getLocation()).map(URI::toString).orElse(null), iss))
				.findAny().orElseThrow(() -> new MissingAuthorizationServerConfigurationException(iss));
	}

	/**
	 * @param  iss                                              the issuer URL
	 * @return                                                  configuration properties associated with the provided issuer URI
	 * @throws MissingAuthorizationServerConfigurationException if configuration properties don not have an entry for the exact issuer (even trailing slash is
	 *                                                          important)
	 */
	public IssuerProperties getIssuerProperties(Object iss) throws MissingAuthorizationServerConfigurationException {
		if (iss == null && issuers.length == 1) {
			return issuers[0];
		}
		return getIssuerProperties(Optional.ofNullable(iss).map(Object::toString).orElse(null));
	}

	@Data
	public static class CorsProperties {
		/**
		 * Path matcher to which this configuration entry applies
		 */
		private String path = "/**";

		/**
		 * Default is "*" which allows all origins
		 */
		private String[] allowedOrigins = { "*" };

		/**
		 * Default is "*" which allows all methods
		 */
		private String[] allowedMethods = { "*" };

		/**
		 * Default is "*" which allows all headers
		 */
		private String[] allowedHeaders = { "*" };

		/**
		 * Default is "*" which exposes all headers
		 */
		private String[] exposedHeaders = { "*" };
	}

	@Data
	public static class IssuerProperties {
		/**
		 * Can be omitted if jwk-set-uri is provided. If provided, it must be exactly the same as in access tokens (even trailing slash, if any, is important).
		 * In case of doubt, open one of your access tokens with a tool like https://jwt.io
		 */
		private URI location;

		/**
		 * Can be omitted if issuer-uri is provided
		 */
		private URI jwkSetUri;

		/**
		 * Can be omitted. Will insert a validator if not null or empty
		 */
		private String audience;

		/**
		 * Authorities mapping configuration, per claim
		 */
		@NestedConfigurationProperty
		private SimpleAuthoritiesMappingProperties[] authorities = { new SimpleAuthoritiesMappingProperties() };

		/**
		 * JSON path for the claim to use as "name" source
		 */
		private String usernameClaim = StandardClaimNames.SUB;
	}

	/**
	 * Configuration for {@link ConfigurableClaimSetAuthoritiesConverter}
	 *
	 * @author ch4mp
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SimpleAuthoritiesMappingProperties {
		/**
		 * JSON path of the claim(s) to map with this properties
		 */
		private String path = "$.realm_access.roles";

		/**
		 * What to prefix authorities with (for instance "ROLE_" or "SCOPE_")
		 */
		private String prefix = "";

		/**
		 * Whether to transform authorities to uppercase, lowercase, or to leave it unchanged
		 */
		private Case caze = Case.UNCHANGED;
	}

	public static enum Case {
		UNCHANGED, UPPER, LOWER
	}

	/**
	 * <ul>
	 * <li>DEFAULT switches between DISABLED if statlessSessions is true (resource server) and SESSION otherwise (client)</li>
	 * <li>DISABLE disables CSRF protection. The default value for resource servers, but <b>you should really not be doing that on a client!</b></li>
	 * <li>SESSION stores CSRF token in servlet session or reactive web-session. The default value for clients, which is just fine if your not querying it with
	 * a JS application (written with Angular, React, Vue, etc.)</li>
	 * <li>COOKIE_HTTP_ONLY stores CSRF in a http-only XSRF-TOKEN cookie (not accessible from rich client apps)</li>
	 * <li>COOKIE_ACCESSIBLE_FROM_JS stores CSRF in a XSRF-TOKEN cookie that is readable by JS apps</li>
	 * </ul>
	 *
	 * @author ch4mp
	 */
	public static enum Csrf {
		/**
		 * Switches between DISABLED if statlessSessions is true (resource server) and SESSION otherwise (client)
		 */
		DEFAULT,
		/**
		 * Disables CSRF protection. The default value for resource servers, but <b>you should really not be doing that on a client!</b>
		 */
		DISABLE,
		/**
		 * Stores CSRF token in servlet session or reactive web-session. The default value for clients, which is just fine if your not querying it with a JS
		 * application (written with Angular, React, Vue, etc.)
		 */
		SESSION,
		/**
		 * Stores CSRF in a http-only XSRF-TOKEN cookie (not accessible from rich client apps)
		 */
		COOKIE_HTTP_ONLY,
		/**
		 * Stores CSRF in a XSRF-TOKEN cookie that is readable by JS apps. To be used when sessions are enabled and queries are issued with Angular, React, Vue,
		 * etc.
		 */
		COOKIE_ACCESSIBLE_FROM_JS
	}

}