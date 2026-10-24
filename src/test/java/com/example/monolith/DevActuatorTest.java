package com.example.monolith;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** dev: everything exposed, runtime log level change works, secrets stay masked, business API untouched. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevActuatorTest {

    static {
        for (String property : TestEnv.properties()) {
            String[] kv = property.split("=", 2);
            System.setProperty(kv[0], kv[1]);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void logLevelChangeTakesEffect() throws Exception {
        String logger = "com.example.monolith.api";
        assertThat(LoggerFactory.getLogger(logger).isTraceEnabled()).isFalse();

        mvc.perform(post("/actuator/loggers/" + logger).with(actuator())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"configuredLevel\":\"TRACE\"}"))
                .andExpect(status().isNoContent());

        assertThat(LoggerFactory.getLogger(logger).isTraceEnabled()).isTrue();
    }

    @Test
    void logLevelChangeRequiresTheActuatorAccount() throws Exception {
        mvc.perform(post("/actuator/loggers/com.example").contentType(MediaType.APPLICATION_JSON)
                .content("{\"configuredLevel\":\"TRACE\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/actuator/loggers/com.example").with(httpBasic("client", "client-password"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuredLevel\":\"TRACE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void secretsAreMaskedEvenForTheActuatorAccount() throws Exception {
        mvc.perform(get("/actuator/env/ACTUATOR_PASSWORD").with(actuator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.property.value").value("******"));
        mvc.perform(get("/actuator/env/spring.application.name").with(actuator()))
                .andExpect(jsonPath("$.property.value").value("monolith"));
    }

    @Test
    void devFeaturesAreAvailable() throws Exception {
        for (String endpoint : new String[] { "logfile", "threaddump", "mappings", "beans", "env", "httpexchanges",
                "scheduledtasks", "prometheus", "metrics" }) {
            mvc.perform(get("/actuator/" + endpoint).with(actuator())
                    .accept(MediaType.ALL)).andExpect(status().isOk());
        }
    }

    @Test
    void businessEndpointIsUnchanged() throws Exception {
        mvc.perform(get("/api/hello")).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Hello, world"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor actuator() {
        return httpBasic(TestEnv.ACTUATOR_USER, TestEnv.ACTUATOR_PASSWORD);
    }
}
