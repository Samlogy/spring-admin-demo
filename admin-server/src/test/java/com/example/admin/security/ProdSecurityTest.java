package com.example.admin.security;

import com.example.admin.StubServer;
import com.example.admin.TestEnv;
import de.codecentric.boot.admin.server.domain.values.InstanceId;
import de.codecentric.boot.admin.server.domain.values.Registration;
import de.codecentric.boot.admin.server.services.InstanceRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization rules of the Admin Server with the prod configuration (OIDC mode, viewer role disabled).
 * Human users are mocked (no IdP needed); the monolith is a local stub.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MockMvcAsyncSupport.class)
@ActiveProfiles("prod")
class ProdSecurityTest {

    private static final StubServer stub = new StubServer();

    private static final String ACTUATOR_USER = "actuator-tech";
    private static final String ACTUATOR_PASSWORD = "actuator-tech-password";

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        for (String property : TestEnv.properties(stub.url())) {
            String[] kv = property.split("=", 2);
            registry.add(kv[0], () -> kv[1]);
        }
    }

    @AfterAll
    static void stopStub() {
        stub.close();
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private InstanceRegistry registry;

    @Autowired
    private AdminSecurityProperties properties;

    private String instance;

    @BeforeEach
    void registerMonolith() {
        InstanceId id = registry.register(Registration.create("monolith", stub.url() + "/actuator/health")
                .managementUrl(stub.url() + "/actuator")
                .metadata("user.name", ACTUATOR_USER)
                .metadata("user.password", ACTUATOR_PASSWORD)
                .build()).block();
        await().atMost(Duration.ofSeconds(10)).until(() -> registry.getInstance(id).block().getEndpoints().isPresent("loggers"));
        instance = "/instances/" + id.getValue();
        stub.actuatorRequests.clear();
    }

    // --- anonymous -------------------------------------------------------------------------------------------

    @Test
    void anonymousBrowserIsRedirectedToTheIdp() throws Exception {
        mvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", endsWith("/oauth2/authorization/adfs")));
    }

    @Test
    void anonymousApiCallIsRejected() throws Exception {
        mvc.perform(get("/applications").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mvc.perform(get(instance + "/actuator/health").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }

    @Test
    void ownActuatorIsNotOnThePublicPort() throws Exception {
        // prod moves the Admin Server's own Actuator to management.server.port (9091), so nothing under
        // /actuator is mapped on the port the ingress publishes: health and info are 404 there, and every
        // other path needs ROLE_ADMIN.
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/info")).andExpect(status().isNotFound());
        for (String endpoint : new String[] { "env", "beans", "metrics", "loggers", "heapdump" }) {
            mvc.perform(get("/actuator/" + endpoint)).andExpect(status().isUnauthorized());
        }
    }

    // --- VIEWER: the role does not exist in prod ---------------------------------------------------------------

    @Test
    void viewerRoleIsDisabledInProd() {
        assertThat(properties.viewerEnabled()).isFalse();
        assertThat(properties.oidc().viewerGroups()).isEmpty();
    }

    @Test
    void viewerCannotReadAnything() throws Exception {
        mvc.perform(get("/").with(viewer())).andExpect(status().isForbidden());
        mvc.perform(get("/applications").with(viewer())).andExpect(status().isForbidden());
        mvc.perform(get(instance + "/actuator/health").with(viewer())).andExpect(status().isForbidden());
        mvc.perform(get(instance + "/actuator/loggers").with(viewer())).andExpect(status().isForbidden());
        mvc.perform(get("/applications/monolith/actuator/metrics").with(viewer())).andExpect(status().isForbidden());
        assertThat(stub.actuatorRequests).isEmpty();
    }

    @Test
    void viewerCannotWriteAnything() throws Exception {
        mvc.perform(post(instance + "/actuator/loggers/com.example").with(viewer()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuredLevel\":\"DEBUG\"}"))
                .andExpect(status().isForbidden());
        // Same call through the application-wide proxy (fans out to every instance).
        mvc.perform(post("/applications/monolith/actuator/loggers/com.example").with(viewer()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuredLevel\":\"DEBUG\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(instance)
                .with(viewer()).with(csrf())).andExpect(status().isForbidden());
        assertThat(stub.actuatorRequests).noneMatch(r -> r.contains("/loggers/com.example"));
    }

    @Test
    void aUserInTheViewerGroupOnlyGetsNoRole() {
        IdpGroupsAuthoritiesMapper mapper =
                new IdpGroupsAuthoritiesMapper(properties.oidc(), properties.viewerEnabled());
        OidcIdToken token = OidcIdToken.withTokenValue("t").subject("carol").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(c -> c.put("groups", List.of("viewer-test"))).build();
        assertThat(mapper.mapAuthorities(List.of(new OidcUserAuthority(token)))).isEmpty();
    }

    // --- ADMIN -----------------------------------------------------------------------------------------------

    @Test
    void adminCanChangeLogLevelWithTheTechnicalActuatorAccount() throws Exception {
        MvcResult result = proxy(post(instance + "/actuator/loggers/com.example").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuredLevel\":\"DEBUG\"}"));

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        String basic = "Basic " + Base64.getEncoder()
                .encodeToString((ACTUATOR_USER + ":" + ACTUATOR_PASSWORD).getBytes(StandardCharsets.UTF_8));
        assertThat(stub.actuatorRequests).contains("POST /actuator/loggers/com.example " + basic);
    }

    @Test
    void adminWriteWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post(instance + "/actuator/loggers/com.example").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuredLevel\":\"DEBUG\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadEnv() throws Exception {
        assertThat(proxy(get(instance + "/actuator/env").with(admin())).getResponse().getStatus()).isEqualTo(200);
    }

    // --- CLIENT (monolith registration account) ---------------------------------------------------------------

    @Test
    void clientCanRegisterWithHttpBasicAndNoCsrfToken() throws Exception {
        mvc.perform(post("/instances").with(httpBasic(TestEnv.CLIENT_USER, TestEnv.CLIENT_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"monolith","healthUrl":"%1$s/actuator/health","managementUrl":"%1$s/actuator"}"""
                                .formatted(stub.url())))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void clientCannotOpenUiOrActuatorProxy() throws Exception {
        RequestPostProcessor client = httpBasic(TestEnv.CLIENT_USER, TestEnv.CLIENT_PASSWORD);
        mvc.perform(get("/").with(client)).andExpect(status().isForbidden());
        mvc.perform(get("/applications").with(client)).andExpect(status().isForbidden());
        mvc.perform(get(instance + "/actuator/health").with(client)).andExpect(status().isForbidden());
        mvc.perform(post(instance + "/actuator/loggers/com.example").with(client)
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuredLevel\":\"DEBUG\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void humansCannotUseHttpBasicInProd() throws Exception {
        // Only the registration account exists locally; humans must log in through the IdP.
        mvc.perform(get("/applications").with(httpBasic("admin", "test-admin-password")))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private static RequestPostProcessor viewer() {
        return SecurityMockMvcRequestPostProcessors.user("alice").roles(Roles.VIEWER);
    }

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("bob").roles(Roles.ADMIN);
    }

    /** Proxied Actuator calls complete asynchronously: wait for the response to be committed. */
    private MvcResult proxy(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        await().atMost(Duration.ofSeconds(10)).until(() -> !result.getRequest().isAsyncStarted());
        return result;
    }
}
