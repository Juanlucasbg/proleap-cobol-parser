# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic Playwright test for the workflow:

- Login with Google
- Open `Negocio` -> `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate:
  - `Información General`
  - `Detalles de la Cuenta`
  - `Tus Negocios`
  - `Términos y Condiciones`
  - `Política de Privacidad`

The test does **not** hardcode any domain and works in dev/staging/prod by using environment variables.

## Requirements

- Node.js 18+
- Network access to the current SaleADS environment

## Install

```bash
cd ui-e2e-saleads
npm install
npx playwright install --with-deps chromium
```

## Run

Set the login URL for the target environment:

```bash
export SALEADS_LOGIN_URL="https://<current-environment>/login"
```

Then run:

```bash
npm test
```

Headed mode:

```bash
npm run test:headed
```

## Evidence outputs

- Screenshots: `ui-e2e-saleads/artifacts/screenshots/`
- Final PASS/FAIL JSON report: `ui-e2e-saleads/artifacts/saleads_mi_negocio_final_report.json`
- Playwright HTML report: `ui-e2e-saleads/playwright-report/`
