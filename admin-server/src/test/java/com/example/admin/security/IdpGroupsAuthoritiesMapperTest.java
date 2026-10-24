package com.example.admin.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdpGroupsAuthoritiesMapperTest {

    private final IdpGroupsAuthoritiesMapper mapper = new IdpGroupsAuthoritiesMapper(new AdminSecurityProperties.Oidc(
            "adfs", List.of("groups", "group", "roles", "role"), Set.of("Admin-Prod"), Set.of("viewer-prod")));

    @Test
    void mapsAdfsGroupClaimToAdmin() {
        assertThat(map(Map.of("group", List.of("Domain Users", "ADMIN-PROD")))).containsExactly("ROLE_ADMIN");
    }

    @Test
    void mapsSingleValuedRoleClaimToViewer() {
        assertThat(map(Map.of("role", "viewer-prod"))).containsExactly("ROLE_VIEWER");
    }

    @Test
    void userInBothGroupsGetsBothRoles() {
        assertThat(map(Map.of("groups", List.of("admin-prod", "viewer-prod"))))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_VIEWER");
    }

    @Test
    void unknownGroupsGiveNoRoleAndClientIsNeverGranted() {
        assertThat(map(Map.of("groups", List.of("CLIENT", "ROLE_CLIENT", "admin-qua")))).isEmpty();
    }

    private Collection<String> map(Map<String, Object> claims) {
        OidcIdToken token = OidcIdToken.withTokenValue("t").subject("u").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).claims(c -> c.putAll(claims)).build();
        Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(List.of(new OidcUserAuthority(token)));
        return mapped.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
