package com.example.monolith;

/** Stand-ins for the environment variables a real deployment provides. */
final class TestEnv {

    static final String ACTUATOR_USER = "actuator-tech";
    static final String ACTUATOR_PASSWORD = "actuator-tech-password";

    private TestEnv() {
    }

    static String[] properties() {
        return new String[] {
                "ACTUATOR_USER=" + ACTUATOR_USER,
                "ACTUATOR_PASSWORD=" + ACTUATOR_PASSWORD,
                "ADMIN_SERVER_URL=http://localhost:1",
                "ADMIN_CLIENT_USER=client",
                "ADMIN_CLIENT_PASSWORD=client-password",
                "LOG_FILE_NAME=target/test-logs/monolith.log",
                // No Admin Server in unit tests.
                "spring.boot.admin.client.auto-registration=false" };
    }
}
