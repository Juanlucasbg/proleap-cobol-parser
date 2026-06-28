# SaleADS Mi Negocio Full Test Report

- Test name: `saleads_mi_negocio_full_test`
- Trigger: cron (`0 * * * *`)
- Executed at: 2026-06-28 17:01 UTC
- Scope: Login with Google + Mi Negocio workflow validation

## Final status by required report fields

| Field | Status | Reason |
|---|---|---|
| Login | FAIL | Google OAuth could not complete. Error shown: "Something went wrong - We weren't able to sign you in. Try again or try another way." |
| Mi Negocio menu | FAIL | Prerequisite failed: login not completed. |
| Agregar Negocio modal | FAIL | Prerequisite failed: login not completed. |
| Administrar Negocios view | FAIL | Prerequisite failed: login not completed. |
| Información General | FAIL | Prerequisite failed: login not completed. |
| Detalles de la Cuenta | FAIL | Prerequisite failed: login not completed. |
| Tus Negocios | FAIL | Prerequisite failed: login not completed. |
| Términos y Condiciones | FAIL | Prerequisite failed: login not completed. |
| Política de Privacidad | FAIL | Prerequisite failed: login not completed. |

## Blocker details

- Visible error text: `Something went wrong - We weren't able to sign you in. Try again or try another way.`
- Authentication stage reached: Google account verification/passkey flow.
- Failure URL observed: `accounts.google.com/v3/signin/challenge/pk/error?...`

## Evidence (screenshots)

- `/tmp/computer-use/1e441.webp` - SaleADS login page with Google option.
- `/tmp/computer-use/8a302.webp` - Google OAuth page with account email entered.
- `/tmp/computer-use/10769.webp` - Passkey verification attempt state.
- `/tmp/computer-use/b4722.webp` - Google sign-in failure screen.

## Legal links validation (required URLs)

- Términos y Condiciones final URL: Not captured (blocked by login prerequisite).
- Política de Privacidad final URL: Not captured (blocked by login prerequisite).
