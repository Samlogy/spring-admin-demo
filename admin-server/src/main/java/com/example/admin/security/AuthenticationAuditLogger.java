package com.example.admin.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Writes login success/failure and logout to the {@code AUDIT} logger so they can be shipped to the
 * log platform (JSON/ECS console output outside dev).
 * <p>
 * The monolith's registration account authenticates with HTTP Basic on every registration call
 * (every 10s), so its successes are logged at DEBUG to keep the audit trail readable; its failures are not.
 */
@Component
public class AuthenticationAuditLogger {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        String roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted()
                .collect(Collectors.joining(","));
        if (auth.getAuthorities().stream().anyMatch(a -> ("ROLE_" + Roles.CLIENT).equals(a.getAuthority()))) {
            audit.debug("event=login.success user={} roles={} remote={}", auth.getName(), roles, remote(auth));
        }
        else {
            audit.info("event=login.success user={} roles={} remote={}", auth.getName(), roles, remote(auth));
        }
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        audit.warn("event=login.failure user={} reason={} remote={}", auth.getName(),
                event.getException().getClass().getSimpleName(), remote(auth));
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        audit.info("event=logout user={} remote={}", auth.getName(), remote(auth));
    }

    private static String remote(Authentication auth) {
        return (auth.getDetails() instanceof WebAuthenticationDetails details) ? details.getRemoteAddress() : "-";
    }
}
