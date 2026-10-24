package com.example.admin;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/** The Admin Server starts with each environment profile and picks up that environment's UI settings. */
class ProfilesContextTest {

    private static StubServer idp;

    @BeforeAll
    static void startIdp() {
        idp = new StubServer();
    }

    @AfterAll
    static void stopIdp() {
        idp.close();
    }

    @ParameterizedTest
    @ValueSource(strings = { "dev", "recf", "qua", "prod" })
    void contextLoads(String profile) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AdminServerApplication.class)
                .profiles(profile)
                .properties(TestEnv.properties(idp.url()))
                .run("--server.port=0")) {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.boot.admin.ui.title")).isEqualTo("Admin " + profile.toUpperCase());
            assertThat(env.getProperty("spring.boot.admin.ui.theme.palette.500")).isEqualTo(switch (profile) {
                case "dev" -> "#4caf50";   // green
                case "recf" -> "#2196f3";  // blue
                case "qua" -> "#ff9800";   // orange
                default -> "#f44336";      // red
            });
            assertThat(env.getProperty("admin.security.mode")).isEqualTo(profile.equals("dev") ? "basic" : "oidc");
        }
    }
}
