# SaleADS E2E tests

This folder contains browser automation for SaleADS workflows using Playwright.

## Test included

- `saleads_mi_negocio_full_test` (`tests/saleads-mi-negocio-full.spec.js`)

## Environment-agnostic execution

The test does not hardcode any SaleADS domain.

- If `SALEADS_LOGIN_URL` (or `BASE_URL`) is provided, the test navigates to that URL.
- If not provided, it assumes the browser context is already at the SaleADS login page.

## Run

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
npm run test:saleads-mi-negocio
```

## Artifacts

Playwright stores artifacts under `playwright-report/` and `test-results/`.

The test also generates:

- checkpoint screenshots:
  - `01-dashboard-loaded.png`
  - `02-mi-negocio-expanded.png`
  - `03-agregar-negocio-modal.png`
  - `04-administrar-negocios-view.png`
  - `05-terminos-y-condiciones.png`
  - `06-politica-de-privacidad.png`
- final JSON report:
  - `final-report.json`
