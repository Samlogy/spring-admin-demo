package com.example.admin;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Effective (resolved) prod values for the security-relevant settings, printed for review and asserted. */
class ProdResolvedPropertiesTest {

    private static final Logger log = LoggerFactory.getLogger(ProdResolvedPropertiesTest.class);

    private static final StubServer idp = new StubServer();

    @AfterAll
    static void stopIdp() {
        idp.close();
    }

    @Test
    void prodValuesMatchTheSpec() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AdminServerApplication.class)
                .profiles("prod").properties(TestEnv.properties(idp.url())).run("--server.port=0")) {
            Environment env = context.getEnvironment();
            log.info("RESOLVED active profiles = {}", List.of(env.getActiveProfiles()));
            for (String key : List.of("admin.security.mode", "admin.security.viewer-enabled",
                    "management.server.port",
                    "server.servlet.session.timeout", "server.servlet.session.cookie.secure",
                    "server.servlet.session.cookie.http-only", "server.servlet.session.cookie.same-site",
                    "server.forward-headers-strategy", "management.endpoints.web.exposure.include",
                    "spring.boot.admin.ui.title", "spring.boot.admin.ui.theme.color")) {
                log.info("RESOLVED {} = {}", key, env.getProperty(key));
            }
            assertThat(env.getProperty("admin.security.mode")).isEqualTo("oidc");
            // prod: no read-only role, and the viewer groups are not even read.
            assertThat(env.getProperty("admin.security.viewer-enabled")).isEqualTo("false");
            assertThat(env.getProperty("admin.security.oidc.viewer-groups")).isEmpty();
            assertThat(env.getProperty("server.servlet.session.timeout")).isEqualTo("30m");
            assertThat(env.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
            assertThat(env.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
            assertThat(env.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
            assertThat(env.getProperty("server.forward-headers-strategy")).isEqualTo("native");
            // The Admin Server's own Actuator: health only, and on a separate internal port.
            assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
            assertThat(env.getProperty("management.endpoint.info.access")).isEqualTo("none");
            assertThat(env.getProperty("management.server.port")).isEqualTo("9091");
        }
    }
}
