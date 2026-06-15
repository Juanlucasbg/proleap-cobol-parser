# SaleADS Mi Negocio E2E

Playwright workflow test for validating the full "Mi Negocio" flow after Google login.

## Scope covered

- Login with Google (including account selection if it appears).
- Sidebar checks and "Mi Negocio" menu expansion.
- "Agregar Negocio" modal validation.
- "Administrar Negocios" page and section validations.
- Legal links ("Terminos y Condiciones" and "Politica de Privacidad"), with new-tab handling.
- Required screenshots and final PASS/FAIL JSON report.

## Environment-agnostic execution

This test does not hardcode a domain. It accepts the current environment login URL by variable:

- `SALEADS_LOGIN_URL` (required for unattended CI execution)

## Run locally

```bash
cd e2e/saleads
npm install
npm run install:browsers
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:saleads-mi-negocio:headed
```

## Artifacts

Playwright stores artifacts under `test-results/`:

- checkpoint screenshots required by the workflow
- trace/video on failure
- `saleads-mi-negocio-final-report.json` with PASS/FAIL per requested section and legal URLs
