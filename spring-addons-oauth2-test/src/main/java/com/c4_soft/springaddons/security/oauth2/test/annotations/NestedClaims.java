/*
 * Copyright 2019 Jérôme Wacongne
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.c4_soft.springaddons.security.oauth2.test.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface NestedClaims {
	String name();

	/**
	 * @return Claims containing JSON number to be parsed as Java int
	 */
	IntClaim[] intClaims() default {};

	/**
	 * @return Claims containing JSON number to be parsed as Java long
	 */
	LongClaim[] longClaims() default {};

	/**
	 * @return Claims containing JSON number to be parsed as Java double
	 */
	DoubleClaim[] doubleClaims() default {};

	/**
	 * @return Claims containing JSON string to be parsed as Java String
	 */
	StringClaim[] stringClaims() default {};

	/**
	 * @return Claims containing JSON string to be parsed as Java URI
	 */
	StringClaim[] uriClaims() default {};

	/**
	 * @return Claims containing JSON string to be parsed as Java URL
	 */
	StringClaim[] urlClaims() default {};

	/**
	 * @return Claims containing JSON number representing the number of seconds from 1970-01-01T00:00:00Z as measured in UTC to be parsed as Java Date
	 */
	IntClaim[] epochSecondClaims() default {};

	/**
	 * @return Claims containing JSON string with "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" format to be parsed as Java Date. "epochSecondClaims" is generally be preferred
	 *         to this representation (this is the case for OpenID claims like "exp", "iat", "auth_time" and "updated_at")
	 */
	StringClaim[] dateClaims() default {};

	/**
	 * @return Claims containing JSON array to be parsed as Java String[]
	 */
	StringArrayClaim[] stringArrayClaims() default {};

	/**
	 * @return claims from a JSON file on the classpath
	 */
	JsonFileClaim[] jsonFiles() default {};

	/**
	 * @return Claims to be parsed as nested Object using a JSON parser
	 */
	JsonObjectClaim[] jsonObjectClaims() default {};

	/**
	 * @return Claims to be parsed as an array of nested Objects using a JSON parser
	 */
	JsonObjectArrayClaim[] jsonObjectArrayClaims() default {};

	public static class Support {
		private static final SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

		private Support() {
		}

		public static Map<String, Object> parse(NestedClaims annotation) throws MalformedURLException, ParseException {
			final var claims = new HashMap<String, Object>();
			for (final var claim : annotation.jsonFiles()) {
				claims.put(claim.name(), ClasspathClaims.Support.parse(claim.value().value()));
			}
			for (final var claim : annotation.jsonObjectClaims()) {
				claims.put(claim.name(), JsonObjectClaim.Support.parse(claim));
			}
			for (final var claim : annotation.intClaims()) {
				claims.put(claim.name(), claim.value());
			}
			for (final var claim : annotation.longClaims()) {
				claims.put(claim.name(), claim.value());
			}
			for (final var claim : annotation.doubleClaims()) {
				claims.put(claim.name(), claim.value());
			}
			for (final var claim : annotation.stringClaims()) {
				claims.put(claim.name(), claim.value());
			}
			for (final var claim : annotation.uriClaims()) {
				claims.put(claim.name(), URI.create(claim.value()));
			}
			for (final var claim : annotation.urlClaims()) {
				claims.put(claim.name(), new URL(claim.value()));
			}
			for (final var claim : annotation.epochSecondClaims()) {
				claims.put(claim.name(), new Date(1000L * claim.value()));
			}
			for (final var claim : annotation.dateClaims()) {
				claims.put(claim.name(), isoFormat.parse(claim.value()));
			}
			for (final var claim : annotation.stringArrayClaims()) {
				claims.put(claim.name(), claim.value());
			}
			for (final var claim : annotation.jsonObjectArrayClaims()) {
				claims.put(claim.name(), JsonObjectArrayClaim.Support.parse(claim));
			}
			return Collections.unmodifiableMap(claims);
		}

	}

}
