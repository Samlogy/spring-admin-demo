package com.example.admin;

/** Stand-ins for the environment variables a real deployment provides. */
public final class TestEnv {

    public static final String CLIENT_USER = "test-client";
    public static final String CLIENT_PASSWORD = "test-client-password";

    private TestEnv() {
    }

    public static String[] properties(String issuerUrl) {
        return new String[] {
                "ADMIN_CLIENT_USER=" + CLIENT_USER,
                "ADMIN_CLIENT_PASSWORD=" + CLIENT_PASSWORD,
                "DEV_ADMIN_PASSWORD=test-admin-password",
                "DEV_VIEWER_PASSWORD=test-viewer-password",
                "OIDC_ISSUER_URI=" + issuerUrl,
                "OIDC_CLIENT_ID=test-client-id",
                "OIDC_CLIENT_SECRET=test-client-secret",
                "OIDC_ADMIN_GROUPS=admin-test",
                "OIDC_VIEWER_GROUPS=viewer-test",
                "MAIL_NOTIFY_ENABLED=false",
                "TEAMS_NOTIFY_ENABLED=false" };
    }
}
