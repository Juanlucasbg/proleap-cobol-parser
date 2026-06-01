# SaleADS Mi Negocio Full Workflow E2E

This folder contains a Playwright test for the `saleads_mi_negocio_full_test` flow:

- Login with Google (or continue if already logged in)
- Open **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios**
- Validate sections:
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
- Validate legal links:
  - Términos y Condiciones
  - Política de Privacidad
- Capture screenshots at key checkpoints
- Produce a final PASS/FAIL JSON report per requested fields

## Why this is environment-agnostic

- The test does **not** hardcode any SaleADS domain.
- Provide the active environment login URL using environment variable:
  - `SALEADS_BASE_URL` (preferred), or
  - `BASE_URL`

## Run

```bash
cd e2e/saleads
npm install
npx playwright install --with-deps chromium
SALEADS_BASE_URL="https://<current-saleads-environment>/login" npm test
```

For headed mode:

```bash
SALEADS_BASE_URL="https://<current-saleads-environment>/login" npm run test:headed
```

## Artifacts

Playwright output includes:

- HTML report (`playwright-report/`)
- Trace/video on failure
- Checkpoint screenshots
- `final-report.json` (contains PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
  - plus captured legal URLs)
