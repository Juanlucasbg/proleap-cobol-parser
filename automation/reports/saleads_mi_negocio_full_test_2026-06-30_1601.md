# SaleADS Mi Negocio Full Test Report

- **Run name:** `saleads_mi_negocio_full_test`
- **Timestamp (UTC):** 2026-06-30 16:01
- **Environment mode:** Environment-agnostic execution (no fixed domain hardcoded)
- **Test account:** `juanlucasbarbiergarzon@gmail.com`

## Final PASS/FAIL by requested report fields

| Field | Status |
|---|---|
| Login | **FAIL** |
| Mi Negocio menu | **FAIL** |
| Agregar Negocio modal | **FAIL** |
| Administrar Negocios view | **FAIL** |
| Información General | **FAIL** |
| Detalles de la Cuenta | **FAIL** |
| Tus Negocios | **FAIL** |
| Términos y Condiciones | **FAIL** |
| Política de Privacidad | **FAIL** |

## Validation details

1. **Login with Google:** Failed at Google OAuth challenge. Flow reached Google auth screens, but no valid password/passkey/session was available to complete login.
2. **Mi Negocio menu:** Not reachable because authenticated dashboard was never reached.
3. **Agregar Negocio modal:** Not reachable due to login prerequisite failure.
4. **Administrar Negocios view:** Not reachable due to login prerequisite failure.
5. **Información General:** Not reachable due to login prerequisite failure.
6. **Detalles de la Cuenta:** Not reachable due to login prerequisite failure.
7. **Tus Negocios:** Not reachable due to login prerequisite failure.
8. **Términos y Condiciones:** Not reachable due to login prerequisite failure; no legal-page URL could be captured.
9. **Política de Privacidad:** Not reachable due to login prerequisite failure; no legal-page URL could be captured.

## URLs observed

- SaleADS public site reached: `https://saleads.ai`
- Authentication blocker URL (Google):  
  `https://accounts.google.com/v3/signin/challenge/pk/error?...`  
  (Exact query string is long and session-specific; blocked on Google "Something went wrong" authentication error page.)

## New tab behavior

- **Not observed** in this run because legal links were not reachable without authenticated access.

## Evidence (screenshots)

- `/workspace/automation/evidence/2026-06-30-1601/01-saleads-home.webp`
- `/workspace/automation/evidence/2026-06-30-1601/02-saleads-login-google.webp`
- `/workspace/automation/evidence/2026-06-30-1601/03-google-email-entry.webp`
- `/workspace/automation/evidence/2026-06-30-1601/04-google-password-screen.webp`
- `/workspace/automation/evidence/2026-06-30-1601/05-google-auth-error.webp`
- `/workspace/automation/evidence/2026-06-30-1601/06-google-auth-blocked-url.webp`

## Blocking reason

Hard prerequisite failure at OAuth authentication: Google sign-in cannot be completed in this environment without valid credential material (password/passkey or an already authenticated browser profile/session).
