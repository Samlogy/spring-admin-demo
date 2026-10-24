# spring-admin-demo

Spring Boot 4.1 monolith (demo) monitored by Spring Boot Admin 4.1, with one Admin Server per environment
(`dev`, `recf`, `qua`, `prod`). Recf/qua/prod log in through ADFS / Entra ID (OIDC).

- Monolith (Admin client): repository root
- Admin Server: [`admin-server/`](admin-server)
- Documentation (architecture, env variables, auth model, prod checklist): [`docs/spring-boot-admin.md`](docs/spring-boot-admin.md)

```bash
cp .env.example .env   # fill the passwords
docker compose up --build
```
