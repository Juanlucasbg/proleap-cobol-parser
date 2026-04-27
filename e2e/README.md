# SaleADS Mi Negocio E2E

This folder contains an end-to-end Playwright test for the workflow:

- Login with Google
- Navigate to **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios**
- Validate account sections and legal links
- Capture screenshots and final PASS/FAIL report

## Requirements

- Node.js 18+
- Playwright browser dependencies installed on the runner
- Access to a SaleADS login page for the target environment

## Environment variables

Set these before running:

- `SALEADS_BASE_URL` (required): login URL for the current SaleADS environment
- `SALEADS_GOOGLE_ACCOUNT` (optional, default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_TEST_BUSINESS_NAME` (optional, default: `Negocio Prueba Automatizacion`)
- `SALEADS_UI_PAUSE_MS` (optional, default: `500`)

No domain is hardcoded in the test. It runs against whichever URL is provided.

## Install

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd e2e
SALEADS_BASE_URL="https://<current-environment-login-url>" npm test
```

For headed mode:

```bash
cd e2e
SALEADS_BASE_URL="https://<current-environment-login-url>" npm run test:headed
```

## Evidence and report outputs

- Playwright artifacts are stored under `e2e/test-results/`
- A JSON final report is attached to the test output and copied to:
  - `e2e/artifacts/mi-negocio-final-report.latest.json`
- Screenshots are taken at these checkpoints:
  - Dashboard loaded
  - Mi Negocio menu expanded
  - Agregar Negocio modal
  - Administrar Negocios page (full page)
  - Términos y Condiciones page
  - Política de Privacidad page
