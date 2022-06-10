# How to extend `OidcAuthentication<OidcToken>`
Lets says that we have business requirements where security is not only role based.

Lets assume that the authorization server also provides us with a `proxies` claim that contains a map of permissions per user "preferredUsername" (what current user was granted to do on behalf of some other users).

This tutorial will demo
- how to extend `OidcAuthentication<OidcToken>` to hold those proxies in addition to authorities
- how to extend security SpEL to easily evaluate proxies granted to authenticated users, OpenID claims or whatever related to security-context

## Start a new project
We'll start with https://start.spring.io/
Following dependencies will be needed:
- lombok

Then add dependencies to spring-addons:
```xml
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-config</artifactId>
        </dependency>
        <dependency>
            <groupId>com.c4-soft.springaddons</groupId>
            <artifactId>spring-security-oauth2-webmvc-addons</artifactId>
            <version>4.3.5</version>
        </dependency>
        <dependency>
            <groupId>com.c4-soft.springaddons</groupId>
            <artifactId>spring-security-oauth2-test-webmvc-addons</artifactId>
            <version>4.3.5</version>
            <scope>test</scope>
        </dependency>
```

An other option would be to use one of `com.c4-soft.springaddons` archetypes (for instance [`spring-webmvc-archetype-singlemodule`](https://github.com/ch4mpy/spring-addons/tree/master/archetypes/spring-weblvc-archetype-singlemodule) or [`spring-webflux-archetype-singlemodule`](https://github.com/ch4mpy/spring-addons/tree/master/archetypes/spring-webflux-archetype-singlemodule))

## Web-security config

### ProxiesAuthentication
Lets first define what a `Proxy` is and our new `Authentication` implementation, with `proxies`:
```java
@Data
public class Proxy implements Serializable {
    private static final long serialVersionUID = 8853377414305913148L;

    private final String proxiedUsername;
    private final String tenantUsername;
    private final Set<String> permissions;

    public Proxy(String proxiedUsername, String tenantUsername, Collection<String> permissions) {
        this.proxiedUsername = proxiedUsername;
        this.tenantUsername = tenantUsername;
        this.permissions = Collections.unmodifiableSet(new HashSet<>(permissions));
    }

    public boolean can(String permission) {
        return permissions.contains(permission);
    }
}

@Data
@EqualsAndHashCode(callSuper = true)
public class ProxiesAuthentication extends OidcAuthentication<OidcToken> {
    private static final long serialVersionUID = 6856299734098317908L;

    private final Map<String, Proxy> proxies;

    public ProxiesAuthentication(OidcToken token, Collection<? extends GrantedAuthority> authorities, Collection<Proxy> proxies, String bearerString) {
        super(token, authorities, bearerString);
        this.proxies = Collections.unmodifiableMap(proxies.stream().collect(Collectors.toMap(Proxy::getProxiedUsername, p -> p)));
    }

    @Override
    public String getName() {
        return getToken().getPreferredUsername();
    }

    public boolean hasName(String username) {
        return Objects.equals(getName(), username);
    }

    public Proxy getProxyFor(String username) {
        return this.proxies.getOrDefault(username, new Proxy(username, getName(), List.of()));
    }
}
```

### Custom method security SpEL handler
```java

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        return new GenericMethodSecurityExpressionHandler<>(ProxiesMethodSecurityExpressionRoot::new);
    }
    
    static final class ProxiesMethodSecurityExpressionRoot extends GenericMethodSecurityExpressionRoot<ProxiesAuthentication> {
        public ProxiesMethodSecurityExpressionRoot() {
            super(ProxiesAuthentication.class);
        }

        public boolean is(String preferredUsername) {
            return getAuth().hasName(preferredUsername);
        }

        public Proxy onBehalfOf(String proxiedUsername) {
            return getAuth().getProxyFor(proxiedUsername);
        }

        public boolean isNice() {
            return hasAnyAuthority("ROLE_NICE_GUY", "SUPER_COOL");
        }
    }
```

### Security @Beans
We'll rely on `spring-security-oauth2-webmvc-addons` `@AutoConfiguration` and add 
- a converter from OidcToken to proxies
- a converter from Jwt to `ProxiesAuthentication` (using existing token and authorities converters plus the new proxies converter)
See [`ServletSecurityBeans`](https://github.com/ch4mpy/spring-addons/blob/master/webmvc/spring-security-oauth2-webmvc-addons/src/main/java/com/c4_soft/springaddons/security/oauth2/config/synchronised/ServletSecurityBeans.java) for provided `@Autoconfiguration`
```java
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig {

    public interface ProxiesConverter extends Converter<OidcToken, Collection<Proxy>> {
    }

    @Bean
    public ProxiesConverter proxiesConverter() {
        return token -> {
            @SuppressWarnings("unchecked")
            final var proxiesClaim = (Map<String, List<String>>) token.getClaims().get("proxies");
            if (proxiesClaim == null) {
                return List.of();
            }
            return proxiesClaim.entrySet().stream().map(e -> new Proxy(e.getKey(), token.getPreferredUsername(), e.getValue())).toList();
        };
    }

    @Bean
    public SynchronizedJwt2AuthenticationConverter<ProxiesAuthentication> authenticationConverter(
            SynchronizedJwt2OidcTokenConverter<OidcToken> tokenConverter,
            JwtGrantedAuthoritiesConverter authoritiesConverter,
            ProxiesConverter proxiesConverter) {
        return jwt -> {
            final var token = tokenConverter.convert(jwt);
            final var authorities = authoritiesConverter.convert(jwt);
            final var proxies = proxiesConverter.convert(token);
            return new ProxiesAuthentication(token, authorities, proxies, jwt.getTokenValue());
        };
    }
}
```
### `application.properties`:
```
com.c4-soft.springaddons.security.token-issuers[0].location=https://localhost:9443/auth/realms/master
com.c4-soft.springaddons.security.token-issuers[0].authorities.claims=realm_access.roles,resource_access.spring-addons.roles
com.c4-soft.springaddons.security.cors[0].path=/greet/**
com.c4-soft.springaddons.security.cors[0].allowed-origins=https://localhost:8100,https://localhost:4200
com.c4-soft.springaddons.security.permit-all=/actuator/health/readiness,/actuator/health/liveness,/v3/api-docs/**
```

## Sample `@RestController`
Note the `@PreAuthorize("is(#username) or isNice() or onBehalfOf(#username).can('greet')")` on the second method, which asserts that the user either:
- is greeting himself
- has one of "nice" authorities
- has permission to "greet" on behalf of "username" passed as `@PathVariable` (the route is `/greet/{username}`)

It comes from the custom method-security expression handler we configured earlier.
``` java
@RestController
@RequestMapping("/greet")
@PreAuthorize("isAuthenticated()")
public class GreetingController {

    @GetMapping()
    @PreAuthorize("hasAuthority('NICE_GUY')")
    public String getGreeting(ProxiesAuthentication auth) {
        return String.format(
            "Hi %s! You are granted with: %s and can proxy: %s.",
            auth.getToken().getPreferredUsername(),
            auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(", ", "[", "]")),
            auth.getProxies().keySet().stream().collect(Collectors.joining(", ", "[", "]")));
    }

    @GetMapping("/{username}")
    @PreAuthorize("is(#username) or isNice() or onBehalfOf(#username).can('greet')")
    public String getGreetingFor(@PathVariable("username") String username) {
        return String.format("Hi %s!", username);
    }
}
```

## Unit-tests

### @ProxiesAuth
`@WithOidcAuth` populates test security-context with an instance of `OicAuthentication<OidcToken>`.
Let's create a `@ProxiesAuth` annotation to inject an instance of `ProxiesAuthentication` instead (with configurable proxies)
```java
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@WithSecurityContext(factory = ProxiesAuth.ProxiesAuthenticationFactory.class)
public @interface ProxiesAuth {

    @AliasFor("authorities")
    String[] value() default { "ROLE_USER" };

    @AliasFor("value")
    String[] authorities() default { "ROLE_USER" };

    OpenIdClaims claims() default @OpenIdClaims();

    Proxy[] proxies() default {};

    String bearerString() default "machin.truc.chose";

    @AliasFor(annotation = WithSecurityContext.class)
    TestExecutionEvent setupBefore() default TestExecutionEvent.TEST_METHOD;

    @Target({ ElementType.METHOD, ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Proxy {
        String onBehalfOf();

        String[] can() default {};
    }

    public static final class ProxiesAuthenticationFactory extends AbstractAnnotatedAuthenticationBuilder<ProxiesAuth, ProxiesAuthentication> {
        @Override
        public ProxiesAuthentication authentication(ProxiesAuth annotation) {
            final var claims = super.claims(annotation.claims());
            final var token = new OidcToken(claims);
            final var proxies = Stream
                .of(annotation.proxies())
                .map(p -> new com.c4soft.springaddons.tutorials.Proxy(p.onBehalfOf(), token.getPreferredUsername(), Stream.of(p.can()).toList()))
                .toList();
            return new ProxiesAuthentication(token, super.authorities(annotation.authorities()), proxies, annotation.bearerString());
        }
    }
}
```

### Controller test
```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.mockmvc.AutoConfigureSecurityAddons;
import com.c4soft.springaddons.tutorials.ProxiesAuth.Proxy;

@WebMvcTest(GreetingController.class)
@AutoConfigureSecurityAddons
@Import(WebSecurityConfig.class)
class GreetingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @ProxiesAuth(
        authorities = { "NICE_GUY", "AUTHOR" },
        claims = @OpenIdClaims(preferredUsername = "Tonton Pirate"),
        proxies = {
            @Proxy(onBehalfOf = "machin", can = { "truc", "bidule" }),
            @Proxy(onBehalfOf = "chose") })
    void whenNiceGuyThenCanBeGreeted() throws Exception {
        mockMvc
            .perform(get("/greet").secure(true))
            .andExpect(status().isOk())
            .andExpect(content().string("Hi Tonton Pirate! You are granted with: [NICE_GUY, AUTHOR] and can proxy: [chose, machin]."));
    }

    @Test
    @ProxiesAuth(authorities = { "AUTHOR" })
    void whenNotNiceGuyThenForbiddenToBeGreeted() throws Exception {
        mockMvc.perform(get("/greet").secure(true)).andExpect(status().isForbidden());
    }

    @Test
    @ProxiesAuth(
        authorities = { "AUTHOR" },
        claims = @OpenIdClaims(preferredUsername = "Tonton Pirate"),
        proxies = { @Proxy(onBehalfOf = "ch4mpy", can = { "greet" }) })
    void whenNotNiceWithProxyThenCanGreetFor() throws Exception {
        mockMvc.perform(get("/greet/ch4mpy").secure(true)).andExpect(status().isOk()).andExpect(content().string("Hi ch4mpy!"));
    }

    @Test
    @ProxiesAuth(
        authorities = { "AUTHOR", "ROLE_NICE_GUY" },
        claims = @OpenIdClaims(preferredUsername = "Tonton Pirate"))
    void whenNiceWithoutProxyThenCanGreetFor() throws Exception {
        mockMvc.perform(get("/greet/ch4mpy").secure(true)).andExpect(status().isOk()).andExpect(content().string("Hi ch4mpy!"));
    }

    @Test
    @ProxiesAuth(
        authorities = { "AUTHOR" },
        claims = @OpenIdClaims(preferredUsername = "Tonton Pirate"),
        proxies = { @Proxy(onBehalfOf = "jwacongne", can = { "greet" }) })
    void whenNotNiceWithoutRequiredProxyThenForbiddenToGreetFor() throws Exception {
        mockMvc.perform(get("/greet/greeted").secure(true)).andExpect(status().isForbidden());
    }

    @Test
    @ProxiesAuth(
        authorities = { "AUTHOR" },
        claims = @OpenIdClaims(preferredUsername = "Tonton Pirate"))
    void whenHimselfThenCanGreetFor() throws Exception {
        mockMvc.perform(get("/greet/Tonton Pirate").secure(true)).andExpect(status().isOk()).andExpect(content().string("Hi Tonton Pirate!"));
    }
}
```