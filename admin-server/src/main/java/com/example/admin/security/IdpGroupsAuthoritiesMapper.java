package com.example.admin.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps the groups/roles sent by the IdP (ADFS claim rules or Entra ID) to ROLE_ADMIN / ROLE_VIEWER.
 * <p>
 * Only these two roles can come out of the IdP: every other authority (OIDC_USER, SCOPE_*...) is dropped,
 * and ROLE_CLIENT is never granted to a human. A user in none of the configured groups is authenticated
 * but gets 403 everywhere.
 */
public class IdpGroupsAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private static final Logger log = LoggerFactory.getLogger(IdpGroupsAuthoritiesMapper.class);

    private final AdminSecurityProperties.Oidc config;
    private final Set<String> adminGroups;
    private final Set<String> viewerGroups;

    public IdpGroupsAuthoritiesMapper(AdminSecurityProperties.Oidc config) {
        this.config = config;
        this.adminGroups = normalize(config.adminGroups());
        this.viewerGroups = normalize(config.viewerGroups());
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<String> userGroups = new HashSet<>();
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidc) {
                collectGroups(oidc.getIdToken().getClaims(), userGroups);
                if (oidc.getUserInfo() != null) {
                    collectGroups(oidc.getUserInfo().getClaims(), userGroups);
                }
            }
            else if (authority instanceof OAuth2UserAuthority oauth2) {
                collectGroups(oauth2.getAttributes(), userGroups);
            }
        }

        Set<GrantedAuthority> mapped = new HashSet<>();
        if (userGroups.stream().anyMatch(adminGroups::contains)) {
            mapped.add(new SimpleGrantedAuthority("ROLE_" + Roles.ADMIN));
        }
        if (userGroups.stream().anyMatch(viewerGroups::contains)) {
            mapped.add(new SimpleGrantedAuthority("ROLE_" + Roles.VIEWER));
        }
        if (mapped.isEmpty()) {
            log.warn("No Admin role mapped: none of the user's groups {} is configured in admin.security.oidc.*-groups",
                    userGroups);
        }
        return mapped;
    }

    private void collectGroups(Map<String, Object> claims, Set<String> target) {
        for (String claimName : config.groupClaims()) {
            Object value = claims.get(claimName);
            if (value instanceof Collection<?> values) {
                values.forEach(v -> target.add(v.toString().toLowerCase(Locale.ROOT)));
            }
            else if (value != null) {
                target.add(value.toString().toLowerCase(Locale.ROOT));
            }
        }
    }

    private static Set<String> normalize(Set<String> groups) {
        return groups.stream()
                .map(String::trim)
                .filter(g -> !g.isEmpty())
                .map(g -> g.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
