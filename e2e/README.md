# SaleADS E2E Tests

This folder contains Playwright tests for SaleADS workflows.

## Implemented flow

- `tests/saleads_mi_negocio_full_test.spec.ts`
  - Login with Google.
  - Open and validate **Mi Negocio** menu.
  - Validate **Agregar Negocio** modal.
  - Open and validate **Administrar Negocios** page sections.
  - Validate **Información General**, **Detalles de la Cuenta** and **Tus Negocios**.
  - Validate **Términos y Condiciones** and **Política de Privacidad** (same tab or popup tab).
  - Capture screenshots on key checkpoints and produce a final PASS/FAIL report.

## Run locally

```bash
cd e2e
npm install
npm run install:browsers
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:saleads-mi-negocio:headed
```

### Environment variables

- `SALEADS_LOGIN_URL` (or `BASE_URL`): Login page URL for the current environment (dev/staging/prod).
- `SALEADS_GOOGLE_ACCOUNT`: Google account email to select if the account picker appears.
- `HEADLESS=false`: Run in headed mode.

The test does not hardcode any SaleADS domain and is intended to run against any environment URL.
