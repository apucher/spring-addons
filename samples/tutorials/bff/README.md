# Implementing the **B**ackend **F**or **F**rontend pattern
In this tutorial, we will implement a n-tier application involving:
- a "rich" JS front-end running in a browser (Angular)
- `spring-cloud-gateway` configured as  BFF
- a Spring Boot 3 servlet REST API configured as an OAuth2 resource server
- Keycloak, Cognito and Auth0 as authorization servers
- two different ways to query the `greetings` API:
  * requests at `/bff/greetings-api/v1/greeting` authorized with a session cookie. This is the BFF pattern and what the Angular app uses.
  * requests at `/greetings-api/v1/greeting` authorized with an access token. This is what Postman or any other OAuth2 client would use.

The latest SNAPSHOT is deployed by CI / CD to a publicly available K8s cluster managed by [OVH](https://www.ovhcloud.com/fr/public-cloud/kubernetes/)): https://bff.demo.c4-soft.com/ui/

## 1. Prerequisites
We assume that [tutorials main README prerequisites section](https://github.com/ch4mpy/spring-addons/tree/master/samples/tutorials#prerequisites) has been achieved and that you have a minimum of 1 OIDC Provider (2 would be better) with ID and secret for clients configured with authorization-code flow.

Also, we will be using spring-addons starters. If for whatever reason you don't want to do so, you'll have to follow:
- the [`reactive-client` tutorial](https://github.com/ch4mpy/spring-addons/tree/master/samples/tutorials/reactive-client) to configure `spring-cloud-gateway` as an OAuth2 client with login and logout (you can skip the authorities mapping section which is not needed here). **Please note that you won't benefit of the back-channel logout implementation if you don't use spring-addons starter**.
- the [`servlet-resource-server` tutorial](https://github.com/ch4mpy/spring-addons/tree/master/samples/tutorials/servlet-resource-server) to configure the REST API as an OAuth2 resource server secured with JWTs

To make core BFF concepts and configuration simpler to grasp, the user will be limited to having a single identity at a time: he'll be able to choose from several identity providers, but will have to logout before he can login with another one (same configuration as in the [`reactive-client` tutorial](https://github.com/ch4mpy/spring-addons/tree/master/samples/tutorials/reactive-client)). For the details of what it requires to allow a user to have several identities at the same time (and how to implement sequential redirections to each identity provider when logging out), refer to the [Resource Server & UI](https://github.com/ch4mpy/spring-addons/tree/master/samples/tutorials/resource-server_with_ui) tutorial.

## 2. `spring-cloud-gateway` as BFF
In theory, Spring cloud gateway is easy to configure as a BFF:
- make it an OAuth2 **client**
- activate the `TokenRelay` filter
- serve both the API and the UI through it

But when it comes to providing with a multi-tenant OAuth2 client with login, logout, CSRF protection with cookies readable by JS applications, token relay and CORS headers correctly handled, things can get complicated to tie together.

### 2.1. The **B**ackend **F**or **F**rontend Pattern
BFF aims at hiding the OAuth2 tokens from the browser. In this pattern, rich applications (Angular, React, Vue, etc.) are secured with sessions on a middle-ware, the BFF, which is the only OAuth2 client and replaces session cookie with an access token before forwarding a request from the browser to the resource server.

There is a big trend toward this pattern because it is considered safer than JS applications configured as OAuth2 public clients as access tokens are:
- kept on the server instead of being exposed to the browser (and frequently to Javascript code)
- delivered to OAuth2 confidential clients (browser apps can't keep a secret and are "public" clients), which reduces the risk that tokens are delivered to programs pretending to be the client we expect

Keep in mind that sessions are a common attack vector and that this two conditions must be met:
- CSRF and BREACH protections must be enabled on the BFF (because browser app security relies on sessions)
- session cookie must be `Secured` (exchanged over https only) and `HttpOnly` (hidden to Javascript code). It being flagged with `SameSite` would be nice.

When user authentication is needed:

0. the browser app redirects the user to a BFF endpoint dedicated to authorization-code initiation
1. the BFF redirects the user to the authorization-server (specifying a callback URL where it expects to receive an authorization code in return)
2. the user authenticates
3. the authorization-server redirects the user back to the BFF with an authorization code
4. the BFF fetches OAuth2 tokens from the authorization-server and stores it in session
5. the BFF redirects the user back to the browser app at an URI specified at step 0.

### 2.2. Project Initialization
From [https://start.spring.io](https://start.spring.io) download a new project with:
- Gateway
- Spring Boot Actuator
- Lombok

Then, we'll add the a dependency to [`spring-addons-webflux-client`](https://central.sonatype.com/artifact/com.c4-soft.springaddons/spring-addons-webflux-client/6.1.5) which is a thin wrapper around `spring-boot-starter-oauth2-client` which pushes auto-configuration from properties one step further. It provides with:
- a `SecurityWebFilterChain` with high precedence  which intercepts all requests matched by `com.c4-soft.springaddons.security.client.security-matchers`
- a logout success handler configured from properties for "almost" OIDC complient providers (Auth0 and Cognito do not implement standrad RP-Initiated Logout)
- a client side implementation for Back-Channel Logout
- a few other features not important in this tutorial (multi-tenancy, as well as authorities mapping and CORS configuration from properties)
```xml
<dependency>
    <groupId>com.c4-soft.springaddons</groupId>
    <artifactId>spring-addons-webflux-client</artifactId>
    <version>${spring-addons.version}</version>
</dependency>
```

### 2.3. Application Properties
Let's first detail the configuration properties used to configure `spring-cloud-gateway`.

The first part defines some constants to be reused later on and, for some of it, be overridden in profiles. You might also consider defining `KEYCLOAK_SECRET`, `AUTH0_SECRET` and `COGNITO_SECRET` environment variables instead of editing the secrets in the following:
```yaml
scheme: http
keycloak-port: 8442
keycloak-issuer: ${scheme}://localhost:${keycloak-port}/realms/master
keycloak-client-id: spring-addons-confidential
keycloak-secret: change-me
cognito-issuer: https://cognito-idp.us-west-2.amazonaws.com/us-west-2_RzhmgLwjl
cognito-client-id: change-me
cognito-secret: change-me
auth0-issuer: https://dev-ch4mpy.eu.auth0.com/
auth0-client-id: change-me
auth0-secret: change-me

gateway-uri: ${scheme}://localhost:${server.port}
greetings-api-uri: ${scheme}://localhost:6443/greetings
angular-uri: ${scheme}://localhost:4200
```
Then comes some standard Spring Boot web application configuration:
```yaml
server:
  port: 8080
  ssl:
    enabled: false

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```
And after that the OAuth2 configuration for an OAuth2 client allowing to users to authenticate (`authorization_code`) from 3 different OIDC Providers
```yaml
  security:
    oauth2:
      client:
        provider:
          keycloak:
            issuer-uri: ${keycloak-issuer}
          cognito:
            issuer-uri: ${cognito-issuer}
          auth0:
            issuer-uri: ${auth0-issuer}
          azure-ad:
            issuer-uri: ${azure-ad-issuer}
        registration:
          keycloak-confidential-user:
            authorization-grant-type: authorization_code
            client-name: Keycloak
            client-id: ${keycloak-client-id}
            client-secret: ${keycloak-secret}
            provider: keycloak
            scope: openid,profile,email,offline_access,roles
          cognito-confidential-user:
            authorization-grant-type: authorization_code
            client-name: Cognito
            client-id: ${cognito-client-id}
            client-secret: ${cognito-secret}
            provider: cognito
            scope: openid,profile,email
          auth0-confidential-user:
            authorization-grant-type: authorization_code
            client-name: Auth0
            client-id: ${auth0-client-id}
            client-secret: ${auth0-secret}
            provider: auth0
            scope: openid,profile,email,offline_access
```
Next, comes the Gateway configuration itself with:
- default filters (applying to all routes):
  * `SaveSession` to ensure that OAuth2 tokens are saved (in session) between requests
  * `DedupeResponseHeader` preventing potentially duplicated CORS headers
- a few routes:
  * `home` is redirecting gateway index to UI one
  * `/bff/greetings-api/v1/**` is forwarding requests to our resource server (`greetings` REST API) according to the BFF pattern (for front-ends secured with sessions):
    - `TokenRelay` filter is applied to replace session cookies with OAuth2 access tokens
    - `StripPrefix` filter removes the first 3 segments of request path (`/bff/greetings-api/v1/greeting/**` will be routed to greetings-api as `/greeting/**`)
  * `/greetings-api/v1/**` is forwarding requests to our resource server (`greetings` REST API) for OAuth2 clients (requests should be authorized with an OAuth2 access token already, so no `TokenRelay filter`)
  * `ui` is forwarding to the Angular app (angular dev server with current localhost conf)
  * `letsencrypt` is needed only when deploying to Kubernetes to route HTTP-01 challenge request when requesting a valid SSL certificate
```yaml
  cloud:
    gateway:
      default-filters:
      - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
      - SaveSession
      routes:
      - id: home
        uri: ${gateway-uri}
        predicates:
        - Path=/
        filters:
        - RedirectTo=301,${gateway-uri}/ui/
      - id: greetings-api-bff
        uri: ${greetings-api-uri}
        predicates:
        - Path=/bff/greetings-api/v1/**
        filters:
        - TokenRelay=
        - StripPrefix=3
      - id: greetings-api-oauth2-clients
        uri: ${greetings-api-uri}
        predicates:
        - Path=/greetings-api/v1/**
        filters:
        - StripPrefix=2
      - id: ui
        uri: ${ui-uri}
        predicates:
        - Path=/ui/**
      - id: letsencrypt
        uri: https://cert-manager-webhook
        predicates:
        - Path=/.well-known/acme-challenge/**
```

Then comes spring-addons configuration for OAuth2 clients:
- `issuers` properties for each of the OIDC Providers we trust (issuer URI, authorities mapping and claim to use as username)
- `client-uri` is used to work with absolute URIs in login process
- `security-matchers` is an array of path matchers for routes processed by the auto-configured client security filter-chain. If null auto-configuration is turned off. Here, it will filter all traffic.
- `permit-all` is a list of path matchers for resources accessible to all requests, even unauthorized ones (end-points not listed here like `/logout` will be accessible only to authenticated users)
  * `/login/**` and `/oauth2/**` are used by Spring during the authorizatoin-code flow
  * `/` and `/ui/**` are there so that unauthorized users can display the Angular app containing a landing page and login buttons
  * `/login-options` and `/me` are end-points on the gateway itself exposing the different URIs to initiate an authorization-code flow (one per client registration above) and current user OpenID claims (empty if unauthorized, which is convenient to display user status in the Angular app)
  * `/v3/api-docs/**` gives a public access to Gateway OpenAPI specification for its `/login-options` and `/me` end-points
- `csrf` with `cookie-accessible-from-js` requires that CSRF tokens are sent in an `XSRF-TOKEN` cookie with `http-enabled=false` so that Angular application can read it and send requests with this token in X`-XSRF-TOKEN` header. It also adds a `WebFilter` for the cookie to be actually added to responses and configures a CSRF handler protecting against BREACH attacks.
- `login-path`, `post-login-redirect-path` and `post-logout-redirect-path` are pretty straight forward. this are relative path to the `client-uri` configured earlier
- `back-channel-logout-enabled` when set to `true`, a `/backchannel-logout` end-point is added, listening for POST requests from the OIDC Providers when a user logs out from another application the current client (useful in SSO environments). This endpoint is secured by a dedicated filter-chain matching only `/backchannel-logout`.
- `oauth2-logout` is the RP-Initiated Logout configuration for OIDC Providers not following the standard (logout endpoint missing from the OpenID configuration or exotic request parameter names)
```yaml
com:
  c4-soft:
    springaddons:
      security:
        issuers:
        - location: ${keycloak-issuer}
          username-claim: preferred_username
          authorities:
          - path: $.realm_access.roles
          - path: $.resource_access.*.roles
        - location: ${cognito-issuer}
          username-claim: username
          authorities:
          - path: cognito:groups
        - location: ${auth0-issuer}
          username-claim: $['https://c4-soft.com/user']['name']
          authorities:
          - path: $['https://c4-soft.com/user']['roles']
          - path: $.permissions
        client:
          client-uri: ${gateway-uri}
          security-matchers: /**
          permit-all:
          - /login/**
          - /oauth2/**
          - /
          - /login-options
          - "/me"
          - /ui/**
          - /v3/api-docs/**
          - /actuator/health/readiness
          - /actuator/health/liveness
          - /.well-known/acme-challenge/**
          csrf: cookie-accessible-from-js
          login-path: /ui/
          post-login-redirect-path: /ui/
          post-logout-redirect-path: /ui/
          back-channel-logout-enabled: true
          oauth2-logout:
            - client-registration-id: cognito-confidential-user
              uri: https://spring-addons.auth.us-west-2.amazoncognito.com/logout
              client-id-request-param: client_id
              post-logout-uri-request-param: logout_uri
            - client-registration-id: auth0-confidential-user
              uri: ${auth0-issuer}v2/logout
              client-id-request-param: client_id
              post-logout-uri-request-param: returnTo
          authorization-request-params:
            auth0-confidential-user:
              - name: audience
                value: demo.c4-soft.com
```
After that, we have Boot configuration for actuator and logs
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
  endpoints:
    web:
      exposure:
        include: '*'
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true

logging:
  level:
    root: INFO
    org:
      springframework:
        security: INFO
```
The last section is a Spring profile to enable SSL, adapt the scheme for our client absolute URIs as well as scheme and port used for the local Keycloak instance:
```yaml
---
spring:
  config:
    activate:
      on-profile: ssl
  cloud:
    gateway:
      default-filters:
      - TokenRelay=
      - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
      - SaveSession
      - SecureHeaders
server:
  ssl:
    enabled: true

scheme: https
keycloak-port: 8443
```

### 2.4. Web Security Configuration
Thanks to [`spring-addons-webflux-client`](https://central.sonatype.com/artifact/com.c4-soft.springaddons/spring-addons-webflux-client/6.1.5), a client security filter-chain is already provided, and we have nothing to do.

#### 2.3.4. Gateway Controller
There are end-points that we will expose from the gateway itself:
- `/login-options` to get a list of available options to initiate an authorization-code flow. This list is build from clients registration repository
- `/me` to get some info about the current user, retrieved from the `Authentication` in the security context (if the user is authenticated, an empty "anonymous" user is returned otherwise).
- `/logout` to invalidate current user session and get the URI of the request to terminate the session on the identity provider. The implementation proposed here builds the RP-Initiated Logout request URI and then executes the same logic as `SecurityContextServerLogoutHandler`, which is the default logout handler.
```java
@RestController
@Tag(name = "Gateway")
public class GatewayController {
	private final ReactiveClientRegistrationRepository clientRegistrationRepository;
	private final SpringAddonsOAuth2ClientProperties addonsClientProps;
	private final LogoutRequestUriBuilder logoutRequestUriBuilder;
	private final ServerSecurityContextRepository securityContextRepository = new WebSessionServerSecurityContextRepository();
	private final List<LoginOptionDto> loginOptions;

	public GatewayController(
			OAuth2ClientProperties clientProps,
			ReactiveClientRegistrationRepository clientRegistrationRepository,
			SpringAddonsOAuth2ClientProperties addonsClientProps,
			LogoutRequestUriBuilder logoutRequestUriBuilder) {
		this.addonsClientProps = addonsClientProps;
		this.clientRegistrationRepository = clientRegistrationRepository;
		this.logoutRequestUriBuilder = logoutRequestUriBuilder;
		this.loginOptions = clientProps.getRegistration().entrySet().stream().filter(e -> "authorization_code".equals(e.getValue().getAuthorizationGrantType()))
				.map(e -> new LoginOptionDto(e.getValue().getProvider(), "%s/oauth2/authorization/%s".formatted(addonsClientProps.getClientUri(), e.getKey())))
				.toList();
	}

	@GetMapping(path = "/login-options", produces = "application/json")
	@Tag(name = "getLoginOptions")
	public Mono<List<LoginOptionDto>> getLoginOptions(Authentication auth) throws URISyntaxException {
		final boolean isAuthenticated = auth instanceof OAuth2AuthenticationToken;
		return Mono.just(isAuthenticated ? List.of() : this.loginOptions);
	}

	@GetMapping(path = "/me", produces = "application/json")
	@Tag(name = "getMe")
	@Operation(responses = { @ApiResponse(responseCode = "200") })
	public Mono<UserDto> getMe(Authentication auth) {
		if (auth instanceof OAuth2AuthenticationToken oauth && oauth.getPrincipal() instanceof OidcUser user) {
			final var claims = new OpenidClaimSet(user.getClaims());
			return Mono.just(
					new UserDto(
							claims.getSubject(),
							Optional.ofNullable(claims.getIssuer()).map(URL::toString).orElse(""),
							oauth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()));
		}
		return Mono.just(UserDto.ANONYMOUS);
	}

	@PutMapping(path = "/logout", produces = "application/json")
	@Tag(name = "logout")
	@Operation(responses = { @ApiResponse(responseCode = "204") })
	public Mono<ResponseEntity<Void>> logout(ServerWebExchange exchange, Authentication authentication) {
		final Mono<URI> uri;
		if (authentication instanceof OAuth2AuthenticationToken oauth && oauth.getPrincipal() instanceof OidcUser oidcUser) {
			uri = clientRegistrationRepository.findByRegistrationId(oauth.getAuthorizedClientRegistrationId()).map(clientRegistration -> {
				final var uriString = logoutRequestUriBuilder
						.getLogoutRequestUri(clientRegistration, oidcUser.getIdToken().getTokenValue(), addonsClientProps.getPostLogoutRedirectUri());
				return StringUtils.hasText(uriString) ? URI.create(uriString) : addonsClientProps.getPostLogoutRedirectUri();
			});
		} else {
			uri = Mono.just(addonsClientProps.getPostLogoutRedirectUri());
		}
		return uri.flatMap(logoutUri -> {
			return securityContextRepository.save(exchange, null).thenReturn(logoutUri);
		}).map(logoutUri -> {
			return ResponseEntity.noContent().location(logoutUri).build();
		});
	}

	static record UserDto(String subject, String issuer, List<String> roles) {
		static final UserDto ANONYMOUS = new UserDto("", "", List.of());
	}

	static record LoginOptionDto(@NotEmpty String label, @NotEmpty String loginUri) {
	}
}
```

## 3. Resource Server
This resource server will expose a single `/greetings` endpoint returning a message with user data retrieved from the **access token** (as oposed to the "client" `/me` endpoint which uses data from the ID token)

### 3.1. Project Initialization
From [https://start.spring.io](https://start.spring.io) download a new project with:
- Spring Web
- Spring Boot Actuator

and then add this dependencies:
- [`spring-addons-webmvc-jwt-resource-server`](https://central.sonatype.com/artifact/com.c4-soft.springaddons/spring-addons-webmvc-jwt-resource-server/6.1.5)
- [`spring-addons-webmvc-test`](https://central.sonatype.com/artifact/com.c4-soft.springaddons/spring-addons-webmvc-test/6.1.5)
- [`swagger-annotations-jakarta`](https://central.sonatype.com/artifact/io.swagger.core.v3/swagger-annotations-jakarta/2.2.8) for a cleaner OpenAPI specification (if the maven `openapi` profile, which is omitted in the tutorial but included in the source, is activted)

### 3.2. Application Properties
The structure is mostly the same as for the BFF (we only remove the `client` part):
```yaml
scheme: http
origins:  ${scheme}://localhost:8080
keycloak-port: 8442
keycloak-issuer: ${scheme}://localhost:${keycloak-port}/realms/master
cognito-issuer: https://cognito-idp.us-west-2.amazonaws.com/us-west-2_RzhmgLwjl
auth0-issuer: https://dev-ch4mpy.eu.auth0.com/

server:
  port: 6443
  error:
    include-message: always
  shutdown: graceful
  ssl:
    enabled: false

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

com:
  c4-soft:
    springaddons:
      security:
        cors:
        - path: /**
          allowed-origins: ${origins}
        issuers:
        - location: ${keycloak-issuer}
          username-claim: preferred_username
          authorities:
          - path: $.realm_access.roles
          - path: $.resource_access.*.roles
        - location: ${cognito-issuer}
          username-claim: username
          authorities:
          - path: cognito:groups
        - location: ${auth0-issuer}
          username-claim: $['https://c4-soft.com/user']['name']
          authorities:
          - path: roles
          - path: permissions
        permit-all: 
        - "/public/**"
        - "/actuator/health/readiness"
        - "/actuator/health/liveness"
        - "/v3/api-docs/**"
        
logging:
  level:
    root: INFO
    org:
      springframework:
        security: INFO
        
management:
  endpoint:
    health:
      probes:
        enabled: true
  endpoints:
    web:
      exposure:
        include: '*'
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true

---
scheme: https
keycloak-port: 8443

server:
  ssl:
    enabled: true

spring:
  config:
    activate:
      on-profile: ssl
```
### 3.3. Web Security Customization
A resource server security filter-chain is auto-configured by spring-addons. Here, we'll define some security configuration to switch successful authorizations from the default `JwtAuthenticationToken` to `OAuthentication<OpenidClaimSet>` (explore its API in the controller if you wonder why):
```java
@Configuration
@EnableMethodSecurity
static class WebSecurityConfig {
  @Bean
  Converter<Jwt, OAuthentication<OpenidClaimSet>> jwtAuthenticationConverter(
      Converter<Map<String, Object>, Collection<? extends GrantedAuthority>> authoritiesConverter,
      SpringAddonsSecurityProperties addonsProperties) {
    return jwt -> new OAuthentication<>(
        new OpenidClaimSet(jwt.getClaims(), addonsProperties.getIssuerProperties(jwt.getClaims().get(JwtClaimNames.ISS)).getUsernameClaim()),
        authoritiesConverter.convert(jwt.getClaims()),
        jwt.getTokenValue());
  }
}
```

### 3.4. REST Controller
Here is the @Controller we will be using:
```java
@RestController
@RequestMapping(path = "/greetings", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Greetings")
public class GreetingsController {
	@GetMapping()
	@Tag(name = "get")
	public GreetingDto getGreeting(OAuthentication<OpenidClaimSet> auth) {
		return new GreetingDto(
				"Hi %s! You are authenticated by %s and granted with: %s.".formatted(auth.getName(), auth.getAttributes().getIssuer(), auth.getAuthorities()));
	}

	public static record GreetingDto(String message) {
	}
}
```

## 4. Browser client
The details of creating an Angular workspace with an application and two client libraries generated with the `openapi-generator-cli` from OpenAPI specifications (itself generated by a maven plugin in our Spring projects) goes beyond the aim of this tutorial.

Make sure you run `npm i` before you `ng serve` the application. This will pull all the necessary dependencies and also generate the client libraries for the Gateway and Greeting APIs (which are documented with OpenAPI).

The important things to note here are:
- we expose a public landing page (accessible to anonymous users)
- the Angular app queries the gateway for the login options it proposes and then renders a page for the user to choose one
- due to security reasons, login and logout redirections are made by setting `window.location.href` (see `UserService`) implementation
- still for security reasons, the logout is a `PUT`. It invalidates the user session on the BFF and returns, in a `location` header, an URI for a `GET` request to invalidate the session on the authorization server (identity provider). It's ok for the second request to be a get becasue it should contain the ID token associated with the session to invalidate (which acts like a CSRF token in this case).
- for CSRF token to be sent, the API calls are issued with relative URLs (`/api/greet` and not `https://localhost:8080/api/greet`)
