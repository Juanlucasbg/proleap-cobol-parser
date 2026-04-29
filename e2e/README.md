# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright test for:

- Google login in SaleADS
- Mi Negocio sidebar flow
- Agregar Negocio modal validations
- Administrar Negocios account page validations
- Legal links (including new-tab handling)
- Checkpoint screenshots and final PASS/FAIL report

## Test file

- `tests/saleads_mi_negocio_full_test.spec.ts`

## Install

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Run

The test assumes the browser is already on the SaleADS login page.

If your browser starts on `about:blank`, set `SALEADS_START_URL`:

```bash
SALEADS_START_URL="https://your-saleads-environment/login" npm run test:saleads-mi-negocio
```

or run without it when already on login:

```bash
npm run test:saleads-mi-negocio
```

## Outputs

- Screenshots: `e2e/screenshots/`
- JSON report: `e2e/reports/playwright-results.json`
- HTML report: `e2e/reports/html-report/`
- Final per-step PASS/FAIL summary in test logs
