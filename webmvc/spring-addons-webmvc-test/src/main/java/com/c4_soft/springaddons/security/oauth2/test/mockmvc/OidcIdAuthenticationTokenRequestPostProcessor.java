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

package com.c4_soft.springaddons.security.oauth2.test.mockmvc;

import com.c4_soft.springaddons.security.oauth2.OAuthentication;
import com.c4_soft.springaddons.security.oauth2.OpenidClaimSet;
import com.c4_soft.springaddons.security.oauth2.test.OAuthenticationTestingBuilder;

public class OidcIdAuthenticationTokenRequestPostProcessor extends OAuthenticationTestingBuilder<OidcIdAuthenticationTokenRequestPostProcessor>
		implements
		AuthenticationRequestPostProcessor<OAuthentication<OpenidClaimSet>> {

	public static OidcIdAuthenticationTokenRequestPostProcessor mockOidcId() {
		return new OidcIdAuthenticationTokenRequestPostProcessor();
	}
}