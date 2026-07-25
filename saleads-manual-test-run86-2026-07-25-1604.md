# SaleADS Mi Negocio Full Test - Run 86

- Automation: `saleads_mi_negocio_full_test`
- Executed at: `2026-07-25 16:04 UTC`
- Environment: SaleADS production entry flow via `saleads.ai` -> `keycloak.saleads.ai`
- Overall result: **BLOCKED**

## Summary

The test could not proceed past Google OAuth authentication for `juanlucasbarbiergarzon@gmail.com`.
Because login is a hard prerequisite, all downstream Mi Negocio validations are marked **BLOCKED**.

## Checkpoint evidence

### Login flow evidence

- `/tmp/computer-use/f8166.webp` - saleads.ai marketing page loaded.
- `/tmp/computer-use/ca4eb.webp` - Keycloak login page loaded.
- `/tmp/computer-use/46d81.webp` - Google OAuth page opened.
- `/tmp/computer-use/4e382.webp` - Email entered.
- `/tmp/computer-use/cc57d.webp` - Password challenge shown.
- `/tmp/computer-use/73838.webp` - Device security code challenge shown.
- `/tmp/computer-use/042c9.webp` - Google sign-in rejection.
- `/tmp/computer-use/11877.webp` - Final blocked state.

### Infrastructure note

- `/tmp/computer-use/8bc45.webp` - Direct `app.saleads.ai` access failed with Cloudflare SSL Error 525.
- Workaround succeeded: `saleads.ai` -> `Sign in` -> `keycloak.saleads.ai`.

## Required final report fields

| Field | Result | Notes |
|---|---|---|
| Login | **FAIL (BLOCKED)** | Google OAuth rejected authentication: "Couldn't sign you in - You didn't provide enough info for Google to be sure this account is really yours." |
| Mi Negocio menu | **BLOCKED** | Not reachable without successful login. |
| Agregar Negocio modal | **BLOCKED** | Not reachable without successful login. |
| Administrar Negocios view | **BLOCKED** | Not reachable without successful login. |
| Informacion General | **BLOCKED** | Not reachable without successful login. |
| Detalles de la Cuenta | **BLOCKED** | Not reachable without successful login. |
| Tus Negocios | **BLOCKED** | Not reachable without successful login. |
| Terminos y Condiciones | **BLOCKED** | Not reachable without successful login; URL not captured. |
| Politica de Privacidad | **BLOCKED** | Not reachable without successful login; URL not captured. |

## Step-by-step status

1. **Login with Google** -> **BLOCKED**
   - Entered account email.
   - Google required password/device verification not available in this automation environment.
2. **Open Mi Negocio menu** -> **BLOCKED** (depends on login)
3. **Validate Agregar Negocio modal** -> **BLOCKED** (depends on login)
4. **Open Administrar Negocios** -> **BLOCKED** (depends on login)
5. **Validate Informacion General** -> **BLOCKED** (depends on login)
6. **Validate Detalles de la Cuenta** -> **BLOCKED** (depends on login)
7. **Validate Tus Negocios** -> **BLOCKED** (depends on login)
8. **Validate Terminos y Condiciones** -> **BLOCKED** (depends on login)
9. **Validate Politica de Privacidad** -> **BLOCKED** (depends on login)

## Captured URLs

- `https://saleads.ai/en` (reachable)
- `https://keycloak.saleads.ai/realms/sale-ads/protocol/openid-connect/auth?...` (reachable)
- `https://accounts.google.com/v3/signin/identifier?...` (reachable)
- `https://accounts.google.com/v3/signin/rejected?...` (final blocked state)

## Blocking reason

Google account security controls prevented unattended authentication from the cloud automation environment. No authenticated SaleADS session was established, so the Mi Negocio workflow could not be executed.
