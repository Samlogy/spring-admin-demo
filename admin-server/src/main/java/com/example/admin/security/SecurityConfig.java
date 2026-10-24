package com.example.admin.security;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.example.admin.security.Roles.ADMIN;
import static com.example.admin.security.Roles.CLIENT;
import static com.example.admin.security.Roles.VIEWER;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.HEAD;
import static org.springframework.http.HttpMethod.POST;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminSecurityProperties.class)
public class SecurityConfig {

    private final AdminServerProperties adminServer;
    private final AdminSecurityProperties properties;

    public SecurityConfig(AdminServerProperties adminServer, AdminSecurityProperties properties) {
        this.adminServer = adminServer;
        this.properties = properties;
    }

    @Bean
    SecurityFilterChain adminSecurityFilterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) throws Exception {

        // IMPORTANT: the Admin Server calls the monolith's Actuator with ITS OWN technical credentials
        // (instance metadata user.name/user.password). Whoever is logged in to the Admin UI therefore acts
        // on the monolith with those credentials, and the monolith cannot tell a viewer from an admin.
        // Role separation must be enforced HERE, on the proxied /instances/{id}/actuator/** paths (and on
        // /applications/{name}/actuator/**, which fans the same call out to every instance of an application).
        // Matchers are evaluated in order: most specific first.
        // readRoles: VIEWER + ADMIN normally, ADMIN only when admin.security.viewer-enabled=false (prod).
        String[] readRoles = readRoles();
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(path("/assets/**"), path("/login"), "/error").permitAll()
                .requestMatchers(path("/oauth2/**"), path("/login/oauth2/**")).permitAll()
                .requestMatchers(path("/actuator/health/**"), path("/actuator/info")).permitAll()
                // Registration API: only the monolith's technical account (and ADMIN for "unregister" in the UI).
                .requestMatchers(POST, path("/instances")).hasRole(CLIENT)
                .requestMatchers(DELETE, path("/instances/*")).hasAnyRole(CLIENT, ADMIN)
                // Sensitive Actuator endpoints, whatever the HTTP method.
                .requestMatchers(proxied("/heapdump/**")).hasRole(ADMIN)
                .requestMatchers(proxied("/env/**")).hasRole(ADMIN)
                // Read-only Actuator calls.
                .requestMatchers(proxied(GET, "/**")).hasAnyRole(readRoles)
                .requestMatchers(proxied(HEAD, "/**")).hasAnyRole(readRoles)
                // Any write on Actuator: log level change, cache clear, env change, ...
                .requestMatchers(proxied("/**")).hasRole(ADMIN)
                .requestMatchers(POST, path("/logout")).authenticated()
                // UI and read APIs.
                .requestMatchers(GET, path("/**")).hasAnyRole(readRoles)
                .requestMatchers(HEAD, path("/**")).hasAnyRole(readRoles)
                // Any other write (unregister an application, notification filters...).
                .anyRequest().hasRole(ADMIN));

        if (properties.mode() == AdminSecurityProperties.Mode.OIDC) {
            String registrationId = properties.oidc().registrationId();
            String authorizationPath = path("/oauth2/authorization/" + registrationId);
            http.oauth2Login(oauth2 -> oauth2
                    .loginPage(authorizationPath)
                    .defaultSuccessUrl(path("/"), false)
                    .userInfoEndpoint(userInfo -> userInfo
                            .userAuthoritiesMapper(
                                    new IdpGroupsAuthoritiesMapper(properties.oidc(), properties.viewerEnabled()))));
            OidcClientInitiatedLogoutSuccessHandler logoutSuccess =
                    new OidcClientInitiatedLogoutSuccessHandler(clientRegistrations.getObject());
            logoutSuccess.setPostLogoutRedirectUri("{baseUrl}" + path("/"));
            http.logout(logout -> logout.logoutUrl(path("/logout")).logoutSuccessHandler(logoutSuccess));
            // The SBA UI sends users to /login when their session expires: go straight to the IdP instead
            // of showing the (unused) username/password form.
            http.addFilterBefore(new RedirectFilter(path("/login"), authorizationPath), BasicAuthenticationFilter.class);
        }
        else {
            SavedRequestAwareAuthenticationSuccessHandler successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
            successHandler.setTargetUrlParameter("redirectTo");
            successHandler.setDefaultTargetUrl(path("/"));
            http.formLogin(form -> form.loginPage(path("/login")).successHandler(successHandler));
            http.logout(logout -> logout.logoutUrl(path("/logout")));
        }

        // HTTP Basic is meant for the technical registration account: in OIDC mode it is the only account
        // in the UserDetailsService, so no human can log in with Basic (humans must go through the IdP).
        http.httpBasic(Customizer.withDefaults());

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(secureCookies).sameSite("Lax"));
        http.csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                // The SBA UI sends the raw XSRF-TOKEN cookie value back in X-XSRF-TOKEN.
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(
                        matcher(POST, path("/instances")),
                        matcher(DELETE, path("/instances/*")),
                        matcher(null, path("/actuator/**"))));
        http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Local accounts. OIDC mode: only the monolith's registration account (ROLE_CLIENT).
     * Basic mode (dev): plus one ADMIN user and, unless the viewer role is disabled, one VIEWER user.
     * All credentials come from environment variables.
     */
    @Bean
    UserDetailsService userDetailsService() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        List<UserDetails> users = new ArrayList<>();
        users.add(user("admin.security.client", properties.client(), CLIENT, encoder));
        if (properties.mode() == AdminSecurityProperties.Mode.BASIC) {
            users.add(user("admin.security.admin", properties.admin(), ADMIN, encoder));
            if (properties.viewerEnabled()) {
                users.add(user("admin.security.viewer", properties.viewer(), VIEWER, encoder));
            }
        }
        return new InMemoryUserDetailsManager(users);
    }

    /** Roles allowed to read the UI and to make GET/HEAD Actuator calls. */
    private String[] readRoles() {
        return properties.viewerEnabled() ? new String[] { VIEWER, ADMIN } : new String[] { ADMIN };
    }

    private static UserDetails user(String prefix, AdminSecurityProperties.Account account, String role,
            PasswordEncoder encoder) {
        Assert.state(account != null && hasText(account.username()) && hasText(account.password()),
                () -> prefix + ".username and " + prefix + ".password must be set");
        return User.withUsername(account.username()).password(encoder.encode(account.password())).roles(role).build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String path(String path) {
        return adminServer.path(path);
    }

    /** Same Actuator sub-path on the instance proxy and on the application-wide proxy. */
    private RequestMatcher[] proxied(String actuatorSubPath) {
        return proxied(null, actuatorSubPath);
    }

    private RequestMatcher[] proxied(HttpMethod method, String actuatorSubPath) {
        return new RequestMatcher[] {
                matcher(method, path("/instances/*/actuator" + actuatorSubPath)),
                matcher(method, path("/applications/*/actuator" + actuatorSubPath)) };
    }

    private static RequestMatcher matcher(HttpMethod method, String pattern) {
        return PathPatternRequestMatcher.withDefaults().matcher(method, pattern);
    }

    /** Forces the deferred CSRF token to load so the XSRF-TOKEN cookie is always sent to the UI. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }

    static final class RedirectFilter extends OncePerRequestFilter {

        private final RequestMatcher from;
        private final String to;

        RedirectFilter(String from, String to) {
            this.from = matcher(GET, from);
            this.to = to;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (from.matches(request)) {
                response.sendRedirect(request.getContextPath() + to);
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
