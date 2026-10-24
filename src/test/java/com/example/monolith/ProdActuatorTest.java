package com.example.monolith;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** prod: separate management port, reduced exposure (no heapdump, no env), Actuator behind the technical account. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ActiveProfiles("prod")
class ProdActuatorTest {

    static {
        for (String property : TestEnv.properties()) {
            String[] kv = property.split("=", 2);
            System.setProperty(kv[0], kv[1]);
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private Environment env;

    @Test
    void exposureExcludesHeapdumpAndEnv() {
        assertThat(env.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,metrics,loggers,prometheus,threaddump,logfile")
                .doesNotContain("heapdump", "env");
        assertThat(get("/actuator/heapdump", true)).isEqualTo(404);
        assertThat(get("/actuator/env", true)).isEqualTo(404);
        assertThat(get("/actuator/httpexchanges", true)).isEqualTo(404);
        assertThat(get("/actuator/beans", true)).isEqualTo(404);
    }

    @Test
    void onlyHealthAndInfoArePublic() {
        assertThat(get("/actuator/health", false)).isEqualTo(200);
        assertThat(get("/actuator/info", false)).isEqualTo(200);
        for (String endpoint : new String[] { "loggers", "metrics", "prometheus", "threaddump", "logfile" }) {
            assertThat(get("/actuator/" + endpoint, false)).as(endpoint).isEqualTo(401);
            assertThat(get("/actuator/" + endpoint, true)).as(endpoint).isEqualTo(200);
        }
    }

    @Test
    void actuatorIsOnlyOnTheManagementPort() throws Exception {
        assertThat(managementPort).isNotEqualTo(serverPort);
        HttpResponse<Void> response = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + serverPort + "/actuator/health")).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(404);
    }

    private int get(String path, boolean authenticated) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + path));
        if (authenticated) {
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    (TestEnv.ACTUATOR_USER + ":" + TestEnv.ACTUATOR_PASSWORD).getBytes(StandardCharsets.UTF_8)));
        }
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
