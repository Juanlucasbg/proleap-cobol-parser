# SaleADS Mi Negocio Full E2E

Playwright test suite to validate the complete Mi Negocio workflow in any SaleADS.ai environment.

## What this test covers

- Login with Google flow handoff and dashboard/sidebar validation.
- Negocio -> Mi Negocio menu expansion.
- Agregar Negocio modal content validation.
- Administrar Negocios view content validation.
- Informacion General, Detalles de la Cuenta, and Tus Negocios checks.
- Legal links (Terminos y Condiciones, Politica de Privacidad) with:
  - new-tab or same-tab handling,
  - heading/content assertions,
  - URL capture,
  - return to application tab.
- Checkpoint screenshots and final JSON report.

## Install

```bash
cd qa/saleads-e2e
npm install
npx playwright install chromium
```

## Run

The test is environment-agnostic and **does not hardcode a domain**. It expects the initial login page URL from `SALEADS_START_URL`.

```bash
SALEADS_START_URL="https://your-env.saleads.ai/login" npm run test:mi-negocio
```

Optional environment variables:

- `SALEADS_GOOGLE_EMAIL`: Google account to select when chooser appears. Default: `juanlucasbarbiergarzon@gmail.com`
- `SALEADS_USER_EMAIL`: Optional expected email in account view. Defaults to `SALEADS_GOOGLE_EMAIL`.
- `SALEADS_USER_NAME`: Optional expected user name text in account view.
- `SALEADS_TEST_BUSINESS_NAME`: Text entered in optional modal input. Default: `Negocio Prueba Automatizacion`
- `SALEADS_E2E_TIMEOUT_MS`: Timeout used by waits and expectations (ms). Default: `30000`

## Outputs

- Screenshots: `qa/saleads-e2e/screenshots/`
- Playwright report: `qa/saleads-e2e/playwright-report/`
- JSON summary: `qa/saleads-e2e/test-results/mi-negocio-report-<timestamp>.json`
