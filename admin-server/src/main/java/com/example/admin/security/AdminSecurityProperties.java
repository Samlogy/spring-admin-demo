package com.example.admin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Set;

/**
 * Admin Server security settings ({@code admin.security.*}).
 *
 * @param mode          {@code basic} (dev: local users) or {@code oidc} (recf/qua/prod: ADFS / Entra login)
 * @param viewerEnabled whether the read-only ROLE_VIEWER exists at all. {@code false} in prod: no viewer user,
 *                      no viewer group mapping, and read access requires ROLE_ADMIN
 * @param client        technical registration account used by the monolith (ROLE_CLIENT, HTTP Basic only)
 * @param admin         dev only: local ROLE_ADMIN user
 * @param viewer        dev only: local ROLE_VIEWER user (ignored when {@code viewerEnabled} is false)
 * @param oidc          mapping of IdP groups/roles to ROLE_ADMIN / ROLE_VIEWER
 */
@ConfigurationProperties("admin.security")
public record AdminSecurityProperties(
        @DefaultValue("oidc") Mode mode,
        @DefaultValue("true") boolean viewerEnabled,
        Account client,
        Account admin,
        Account viewer,
        @DefaultValue Oidc oidc) {

    public enum Mode { BASIC, OIDC }

    public record Account(String username, String password) {
    }

    /**
     * @param registrationId Spring Security client registration id ({@code spring.security.oauth2.client.registration.<id>})
     * @param groupClaims    claims that may carry the user's groups/roles (ADFS: {@code group}/{@code role}, Entra: {@code groups}/{@code roles})
     * @param adminGroups    group/role values granting ROLE_ADMIN (case-insensitive)
     * @param viewerGroups   group/role values granting ROLE_VIEWER (case-insensitive)
     */
    public record Oidc(
            @DefaultValue("adfs") String registrationId,
            @DefaultValue({"groups", "group", "roles", "role"}) List<String> groupClaims,
            @DefaultValue Set<String> adminGroups,
            @DefaultValue Set<String> viewerGroups) {
    }
}
