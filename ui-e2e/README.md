# SaleADS Mi Negocio E2E

This folder contains a Playwright test that validates the full Mi Negocio workflow:

- Login with Google (including account selector handling)
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page sections
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones and Política de Privacidad legal links
- Final PASS/FAIL report output

## Requirements

- Node.js 18+
- Playwright Chromium browser

## Install

```bash
cd ui-e2e
npm install
npm run test:install
```

## Run

Prefer one of the following runtime options (no hardcoded domain in code):

1. Set `SALEADS_URL` for the current environment login page.
2. Or set `BASE_URL`.
3. Or configure Playwright `baseURL`.

Example:

```bash
cd ui-e2e
SALEADS_URL="https://<your-environment>/login" npm test
```

Headed mode:

```bash
cd ui-e2e
HEADLESS=false SALEADS_URL="https://<your-environment>/login" npm run test:headed
```

## Artifacts

- Screenshots are attached at checkpoints.
- Final report JSON:
  - `test-results/.../saleads-mi-negocio-final-report.json`
- HTML report:
  - `playwright-report/index.html`
