# Spring Boot Admin: monolith monitoring (dev / recf / qua / prod)

## 1. Architecture

One Admin Server per environment, built from the same code and image. Only the Spring profile and the environment variables change. The monolith is the Admin client.

```
             users (browser)                                   ADFS / Entra ID (MFA enforced there)
                   |  HTTPS                                               ^
                   v                                                      | OIDC (authorization code)
   +----------- ingress / reverse proxy (TLS, VPN / IP allowlist) --------+
   |                                                                      |
   v                                                                      |
 +-------------------------------+   1. POST /instances (registration account, Basic, ROLE_CLIENT)
 | Admin Server  (profile <env>) | <------------------------------------------------+
 |  :8081                        |                                                  |
 |  role checks on               |   2. GET/POST <mgmt>/actuator/** (Actuator account,|
 |  /instances/*/actuator/**     | ---- Basic, role ACTUATOR, from instance metadata)-+--> +---------------------------+
 +-------------------------------+                                                      | monolith (profile <env>)  |
                                                                                        |  :8080 app (+ /livez /readyz)
                                                                                        |  :9090 Actuator (qua/prod)|
                                                                                        +---------------------------+
```

| Component | Location | Versions |
|---|---|---|
| Monolith (Admin client) | repository root (`pom.xml`, `src/`) | Spring Boot 4.1.2, Java 21, Spring MVC, Maven |
| Admin Server | `admin-server/` (separate Maven project) | Spring Boot Admin 4.1.4 (built against Boot 4.1.1, same 4.1 line), Spring Security 7.1 |

> The repository was empty, so the monolith here is a small **demo**: an in-memory CRUD API (`/api/products`), one REST endpoint and one `@Scheduled` job. See the README for run instructions and API examples. To apply this to the real monolith, copy the `pom.xml` dependencies and plugins, `config/ActuatorSecurityConfig.java`, `config/ActuatorConfig.java` and the `application*.yml` settings.

## 2. Environment variables

✅ = required, ◻ = optional (default in brackets), – = not used.

### Monolith

| Variable | dev | recf | qua | prod | Purpose |
|---|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `recf` | `qua` | `prod` | Environment |
| `ADMIN_SERVER_URL` | ◻ [`http://localhost:8081`] | ✅ | ✅ | ✅ | URL of this environment's Admin Server |
| `ADMIN_CLIENT_USER` / `ADMIN_CLIENT_PASSWORD` | ✅ | ✅ | ✅ | ✅ | Registration account (ROLE_CLIENT on the Admin Server) |
| `ACTUATOR_USER` / `ACTUATOR_PASSWORD` | ✅ | ✅ | ✅ | ✅ | Actuator account (role ACTUATOR on the monolith); passed to the Admin through instance metadata |
| `MANAGEMENT_PORT` | – | – | ◻ [9090] | ◻ [9090] | Separate Actuator port |
| `LOG_FILE_NAME` | ◻ [`logs/monolith.log`] | ◻ [`/var/log/app/monolith.log`] | ◻ [same] | ◻ [same] | Log file served by `/actuator/logfile` |
| `ADMIN_CLIENT_SERVICE_HOST_TYPE` | ◻ [`ip`] | ◻ | ◻ | ◻ | How the instance advertises itself (`ip`, `host-name`, `canonical-host-name`) |
| `SPRING_BOOT_ADMIN_CLIENT_INSTANCE_SERVICEBASEURL`, `..._MANAGEMENTBASEURL` | ◻ | ◻ | ◻ | ◻ | Force the advertised URLs (e.g. a K8s Service per pod) |

### Admin Server

| Variable | dev | recf | qua | prod | Purpose |
|---|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `recf` | `qua` | `prod` | Environment (recf/qua/prod also turn on the `oidc` profile) |
| `ADMIN_PORT` | ◻ [8081] | ◻ | ◻ | ◻ | HTTP port |
| `ADMIN_CLIENT_USER` / `ADMIN_CLIENT_PASSWORD` | ✅ | ✅ | ✅ | ✅ | Same values as the monolith's |
| `DEV_ADMIN_USER` / `DEV_ADMIN_PASSWORD` | ◻ [admin] / ✅ | – | – | – | Local ADMIN user (dev only) |
| `DEV_VIEWER_USER` / `DEV_VIEWER_PASSWORD` | ◻ [viewer] / ✅ | – | – | – | Local VIEWER user (dev only) |
| `OIDC_ISSUER_URI` | – | ✅ | ✅ | ✅ | ADFS: `https://<adfs-host>/adfs`; Entra ID: `https://login.microsoftonline.com/<tenant-id>/v2.0` |
| `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` | – | ✅ | ✅ | ✅ | Confidential client registered in the IdP (one per environment) |
| `OIDC_ADMIN_GROUPS` | – | ✅ | ✅ | ✅ | Comma-separated group/role values that grant ROLE_ADMIN (e.g. `SBA-Admin-PROD`) |
| `OIDC_VIEWER_GROUPS` | – | ✅ | ✅ | ✅ | Comma-separated group/role values that grant ROLE_VIEWER (e.g. `SBA-Viewer-PROD`) |
| `OIDC_GROUP_CLAIMS` | – | ◻ [`groups,group,roles,role`] | ◻ | ◻ | Claims that carry the groups |
| `OIDC_USERNAME_ATTRIBUTE` | – | ◻ [`upn`] | ◻ | ◻ | Claim used as the user name (audit logs, UI) |
| `OIDC_SCOPES` | – | ◻ [`openid,profile,email,allatclaims`] | ◻ | ◻ | For Entra ID, drop `allatclaims` |
| `MAIL_NOTIFY_ENABLED` | ◻ [false] | ◻ [false] | ◻ [true] | ◻ [true] | Mail notifications |
| `MAIL_HOST`, `MAIL_PORT` [587], `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_NOTIFY_TO`, `MAIL_NOTIFY_FROM` | – | ◻ | ✅ if mail is on | ✅ if mail is on | SMTP settings. Without `MAIL_HOST` there is no mail sender, so no mail notifier |
| `TEAMS_NOTIFY_ENABLED` | ◻ [false] | ◻ [false] | ◻ [true] | ◻ [true] | Microsoft Teams notifications |
| `TEAMS_WEBHOOK_URL` | – | ◻ | ✅ if Teams is on | ✅ if Teams is on | Teams Workflows webhook (secret: the URL itself grants posting) |

`.env.example` lists the variables used for local runs.

## 3. Run locally (dev)

### With Docker Compose
```bash
cp .env.example .env            # fill the empty passwords: openssl rand -base64 32
docker compose up --build
```
- Admin UI: http://localhost:8081. Log in as `DEV_ADMIN_USER` or `DEV_VIEWER_USER`. The title is **Admin DEV** with a green theme.
- Monolith: http://localhost:8080/api/hello. It registers as `monolith-dev` with the tag `environment: dev`.

### Without Docker
```bash
# terminal 1
cd admin-server
ADMIN_CLIENT_USER=client ADMIN_CLIENT_PASSWORD=... DEV_ADMIN_PASSWORD=... DEV_VIEWER_PASSWORD=... \
  mvn spring-boot:run -Dspring-boot.run.profiles=dev
# terminal 2 (repository root)
ACTUATOR_USER=actuator ACTUATOR_PASSWORD=... ADMIN_CLIENT_USER=client ADMIN_CLIENT_PASSWORD=... \
  mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Tests
```bash
mvn verify                       # monolith: 14 tests
(cd admin-server && mvn verify)  # admin server: 22 tests, no IdP needed (in-process stub issuer + mocked users)
```

## 4. What each environment exposes, and why

| Endpoint | dev | recf | qua | prod | Notes |
|---|---|---|---|---|---|
| health, info | ✅ public | ✅ public | ✅ public | ✅ public | Details only for the ACTUATOR role |
| metrics, prometheus, loggers, threaddump, logfile | ✅ | ✅ | ✅ | ✅ | Day-to-day operations. `loggers` is writable |
| env | ✅ | ✅ | ✅ | ❌ (off) | Values shown only to ACTUATOR, with secret-like keys and URI credentials masked. Off in prod: configuration is sensitive |
| beans, mappings, scheduledtasks | ✅ | ✅ | ✅ | ❌ | Diagnostics, not needed in prod |
| httpexchanges | ✅ | ✅ | ✅ | ❌ (recording off) | In-memory, capped at 100 entries. Off in prod because it records request metadata |
| heapdump | ✅ | ❌ | ❌ | ❌ (off) | Memory can contain secrets and personal data. Boot 4 turns it off by default; only dev turns it on |
| configprops, conditions, sbom… | ✅ (`*`) | ❌ | ❌ | ❌ | |
| caches, flyway, liquibase, quartz | – | – | – | – | Not used by this app, so not exposed. Add them to the recf/qua lists if the app adopts them |
| Actuator port | 8080 | 8080 | **9090** | **9090** | qua/prod: separate port, reachable only from the Admin Server |

Kubernetes probes: on 8080 use `/livez` and `/readyz` (the additional paths). Alternatively, on 9090 use `/actuator/health/liveness` and `/actuator/health/readiness`.

`/actuator/info` shows `build` (version, time, from `build-info`) and `git` (branch, commit, from `git.properties`). That's how the Admin UI shows which version runs in each environment.

## 5. Authentication and authorization

### Roles (Admin Server)

| Role | Who | Can |
|---|---|---|
| `ROLE_VIEWER` | IdP groups in `OIDC_VIEWER_GROUPS` (dev: `DEV_VIEWER_USER`) | UI, every **GET** Actuator call except `heapdump` and `env` |
| `ROLE_ADMIN` | IdP groups in `OIDC_ADMIN_GROUPS` (dev: `DEV_ADMIN_USER`) | Everything: log level changes, heapdump, env, cache clear, unregister, any write |
| `ROLE_CLIENT` | the monolith's registration account (local, HTTP Basic) | `POST /instances` and `DELETE /instances/*` only. **Gets 403 on the UI and the Actuator proxy** |

`IdpGroupsAuthoritiesMapper` maps IdP groups to these roles. It reads the claims listed in `OIDC_GROUP_CLAIMS`, compares values case-insensitively and drops every other authority. A human can never receive `ROLE_CLIENT`. A user who belongs to no configured group is authenticated but gets 403 everywhere.

### Why the checks live in the Admin Server
The Admin Server calls the monolith's Actuator with **its own technical credentials** (the ACTUATOR account, sent by the monolith in its registration metadata). To the monolith, every Admin user looks identical. Role separation is therefore enforced in the Admin Server, on the proxied paths:

| Rule (first match wins) | Access |
|---|---|
| `/assets/**`, `/login`, `/oauth2/**`, `/login/oauth2/**`, own `/actuator/health/**` and `/actuator/info` | public |
| `POST /instances` | CLIENT |
| `DELETE /instances/*` | CLIENT or ADMIN (ADMIN for the UI's "unregister" button) |
| `/instances/*/actuator/heapdump/**`, `/instances/*/actuator/env/**` | ADMIN |
| `GET`/`HEAD /instances/*/actuator/**` | VIEWER or ADMIN |
| any other method on `/instances/*/actuator/**` | ADMIN |
| `POST /logout` | authenticated |
| any other `GET`/`HEAD` | VIEWER or ADMIN |
| anything else | ADMIN |

The same Actuator rules also apply to **`/applications/*/actuator/**`**. That SBA endpoint sends one call to every instance of an application, so without these rules a VIEWER could change log levels through it.

CSRF uses `CookieCsrfTokenRepository.withHttpOnlyFalse()`: the UI reads `XSRF-TOKEN` and sends `X-XSRF-TOKEN`. CSRF is skipped only for `POST /instances`, `DELETE /instances/*` and the server's own `/actuator/**`.

HTTP Basic is turned on, but in recf/qua/prod the only local account is the registration account. Humans can only log in through the IdP.

Sessions last 30 minutes. The cookie `ADMIN_SESSION` is `Secure`, `HttpOnly` and `SameSite=Lax` (dev only drops `Secure`, for http://localhost). `server.forward-headers-strategy=native` trusts `X-Forwarded-*` from private-network proxies, i.e. the ingress.

Audit: the `AUDIT` logger records `login.success`, `login.failure` and `logout`, with user, roles and client IP. Outside dev it writes ECS JSON to stdout. The monolith's registration logins happen every 10 s, so they are logged at DEBUG.

### Technical accounts

| Account | Lives in | Used for | Role |
|---|---|---|---|
| Registration (`ADMIN_CLIENT_*`) | Admin Server (local user) + monolith | monolith → Admin `POST /instances` | `ROLE_CLIENT` |
| Actuator (`ACTUATOR_*`) | monolith (`spring.security.user`) + registration metadata | Admin → monolith Actuator | `ACTUATOR` |

The two accounts **must be different**, with one pair per environment. Generate each password at random (32+ bytes) and store it in the secret manager. The Admin UI masks `user.password` in metadata (`spring.boot.admin.metadata-keys-to-sanitize`).

**Rotation** (one environment at a time):
1. Write the new value to the secret manager.
2. Registration account: restart the Admin Server and the monolith together. Until both have the new value, registration fails and the instance shows OFFLINE.
3. Actuator account: roll out the monolith. Each new pod re-registers with the new metadata, so the Admin picks up the new credentials. No Admin restart is needed.
4. Rotate at least yearly, and immediately when someone with access leaves.

### IdP setup (ADFS, "Entra with login/password")
- **ADFS**: create an *Application Group* of type "Server application accessing a web API" (or "Web browser accessing a web application" with a confidential client):
  - Redirect URI: `https://<admin-host>/login/oauth2/code/adfs` (one per environment).
  - Client secret → `OIDC_CLIENT_SECRET`. Client id → `OIDC_CLIENT_ID`.
  - Issuance transform rules: *Send LDAP Attributes as Claims*, mapping *Token-Groups – Unqualified Names* → *Group* (JWT claim `group`) and *User-Principal-Name* → *UPN* (`upn`).
  - The `allatclaims` scope (already in `OIDC_SCOPES`) copies those claims into the ID token.
  - Access control policy: **"Permit specific group and require MFA"**, restricted to the SBA groups. MFA is enforced here, not in the application.
- **Entra ID** (if you move from ADFS): use App Roles (`roles` claim) or `groups` (sent as **object IDs**, so put the GUIDs in `OIDC_*_GROUPS`). Set `OIDC_SCOPES=openid,profile,email`, `OIDC_USERNAME_ATTRIBUTE=preferred_username`, and enforce MFA with Conditional Access.
- Suggested groups: `SBA-Admin-RECF`, `SBA-Viewer-RECF`, `SBA-Admin-QUA`, `SBA-Viewer-QUA`, `SBA-Admin-PROD`, `SBA-Viewer-PROD`. Keep the PROD admin group small.

## 6. Notifications
Mail and Microsoft Teams (`spring.boot.admin.notify.*`) fire on every status change except `UNKNOWN → UP`. That covers UP → DOWN/OFFLINE and the recovery back to UP. They default to on in qua/prod and off in dev/recf, and each can be switched with its `*_NOTIFY_ENABLED` variable. Teams titles carry the environment (`[PROD] Status changed`).

## 7. Kubernetes notes
- **Admin Server**: run **1 replica**. SBA keeps its event store in memory; several replicas would each hold a different view unless you add Hazelcast. The ingress has TLS and a VPN or IP allowlist.
- **Monolith**: instances register with their **pod IP** (`service-host-type: ip`), so each replica is monitored on its own.
- **Actuator port 9090** (qua/prod): do not put it in the ingress. Restrict it with a NetworkPolicy:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: monolith-actuator-from-admin-only
spec:
  podSelector:
    matchLabels: { app: monolith }
  policyTypes: [Ingress]
  ingress:
    - from:                                   # app traffic (ingress controller, other services)
        - namespaceSelector: {}
      ports: [{ port: 8080 }]
    - from:                                   # Actuator: Admin Server only (+ Prometheus if it scrapes 9090)
        - namespaceSelector: { matchLabels: { kubernetes.io/metadata.name: monitoring } }
          podSelector: { matchLabels: { app: admin-server } }
      ports: [{ port: 9090 }]
```
- In recf, Actuator stays on 8080: block `/actuator/**` (except health and info) at the ingress.

## 8. Prod security checklist
- [ ] Admin reachable only over HTTPS, behind a VPN or IP allowlist; no public DNS record
- [ ] MFA enforced by the ADFS access control policy (or Entra Conditional Access) for the Admin application
- [ ] `OIDC_ADMIN_GROUPS` / `OIDC_VIEWER_GROUPS` point to the PROD groups; admin group membership reviewed
- [ ] Registration and Actuator accounts are distinct, random, stored in the secret manager, and have a rotation date
- [ ] `ADMIN_SERVER_URL` is `https://`, because the registration carries the Actuator credentials
- [ ] Monolith Actuator port 9090 is not in the ingress; NetworkPolicy/firewall allows only the Admin (and Prometheus)
- [ ] `heapdump` and `env` absent from `/actuator` on prod (`ProdActuatorTest` checks this)
- [ ] Session cookie is `Secure`/`HttpOnly`/`SameSite=Lax`, 30 min timeout (`ProdResolvedPropertiesTest`)
- [ ] `AUDIT` logs and Admin stdout are shipped to the log platform, with an alert on repeated `login.failure`
- [ ] Notifications (mail/Teams) tested once by stopping a qua instance
- [ ] Container runs as non-root (uid 10001), image scanned, Admin is 1 replica

## 9. Trade-offs
- **Monolith Spring Security**: the Actuator chain covers Actuator requests only. The demo's business endpoints stay open (`permitAll`, no CSRF, since there is no login or session). In the real monolith, keep its existing chain instead.
- **Actuator credentials in metadata**: this is the simplest setup, as asked. The alternative is `spring.boot.admin.instance-auth.*` on the Admin Server, which keeps the credentials off the registration call.
- **Group matching** is exact (case-insensitive). Configure the names exactly as the IdP emits them (unqualified for ADFS, GUIDs for Entra `groups`).
