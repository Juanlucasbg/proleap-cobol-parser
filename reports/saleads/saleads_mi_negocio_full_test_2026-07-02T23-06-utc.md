# SaleADS Mi Negocio Full Test Report

- Test name: `saleads_mi_negocio_full_test`
- Executed at (UTC): `2026-07-02T23:06`
- Environment policy: URL-agnostic (no hardcoded SaleADS domain in workflow logic)
- Overall result: **FAIL** (authentication blocker before app dashboard)

## Execution Summary

The browser flow reached Google OAuth, but authentication was blocked at:

- Final URL: `https://accounts.google.com/v3/signin/rejected`
- Message: `Couldn't sign you in. You didn't provide enough info for Google to be sure this account is really yours.`

Because login did not complete, the application dashboard and all post-login "Mi Negocio" validations were not reachable in this run.

## PASS/FAIL Matrix

| Area | Status | Evidence |
|---|---|---|
| Login | FAIL | Google authentication rejected on device-recognition checkpoint |
| Mi Negocio menu | FAIL | Blocked by failed login |
| Agregar Negocio modal | FAIL | Blocked by failed login |
| Administrar Negocios view | FAIL | Blocked by failed login |
| Información General | FAIL | Blocked by failed login |
| Detalles de la Cuenta | FAIL | Blocked by failed login |
| Tus Negocios | FAIL | Blocked by failed login |
| Términos y Condiciones | FAIL | Blocked by failed login |
| Política de Privacidad | FAIL | Blocked by failed login |

## Step Evidence

### 1) Login with Google
- Navigated to `saleads.ai` after `app.saleads.ai` returned SSL handshake error.
- Clicked Sign in and reached Keycloak login page.
- Clicked Continue with Google.
- Entered account email: `juanlucasbarbiergarzon@gmail.com`.
- Attempted available Google flows (`Try another way`) but all converged to sign-in rejection page.

### 2-9) Mi Negocio workflow and legal pages
- Not executable due to failed authentication prerequisite.
- Final URLs for legal pages were not captured because those links are inside the authenticated application.

## Screenshot Checkpoints

- `/tmp/computer-use/91170.webp` - SaleADS landing page loaded
- `/tmp/computer-use/46ecb.webp` - Keycloak login page with Google button
- `/tmp/computer-use/97590.webp` - Google email entered
- `/tmp/computer-use/d37f7.webp` - Google auth step
- `/tmp/computer-use/73368.webp` - Auth method selection
- `/tmp/computer-use/d834a.webp` - Recovery/auth challenge step
- `/tmp/computer-use/b133a.webp` - Security code challenge step
- `/tmp/computer-use/c8151.webp` - Terminal blocker ("Couldn't sign you in")

## Notes

- This run followed the requested UI waiting and visible-text navigation approach.
- To validate steps 2-9 successfully in an autonomous environment, a pre-authenticated browser/session or a test auth bypass is required.
