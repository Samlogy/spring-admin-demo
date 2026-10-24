package com.example.monolith;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/** The monolith starts with each environment profile and registers under an env-specific name and tag. */
class ProfilesContextTest {

    @ParameterizedTest
    @ValueSource(strings = { "dev", "recf", "qua", "prod" })
    void contextLoads(String profile) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MonolithApplication.class)
                .profiles(profile)
                .properties(TestEnv.properties())
                .run("--server.port=0", "--management.server.port=" + (profile.matches("qua|prod") ? "0" : ""))) {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.application.name")).isEqualTo("monolith");
            assertThat(env.getProperty("spring.boot.admin.client.instance.name")).isEqualTo("monolith-" + profile);
            assertThat(env.getProperty("spring.boot.admin.client.instance.metadata[tags.environment]")).isEqualTo(profile);
            assertThat(env.getProperty("spring.boot.admin.client.instance.metadata[user.name]"))
                    .isEqualTo(TestEnv.ACTUATOR_USER)
                    .isNotEqualTo(env.getProperty("spring.boot.admin.client.username"));
        }
    }
}
