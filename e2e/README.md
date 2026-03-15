# SaleADS E2E tests

This folder contains standalone Playwright tests for SaleADS workflows.

## Test included

- `saleads_mi_negocio_full_test`: logs in with Google and validates the Mi Negocio module end-to-end, including:
  - sidebar and menu checks
  - Agregar Negocio modal checks
  - Administrar Negocios account page checks
  - legal links (Terminos and Politica) with new-tab/same-tab handling
  - screenshots at critical checkpoints
  - final per-step PASS/FAIL report with captured legal URLs

## Setup

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd e2e
SALEADS_BASE_URL="https://<current-saleads-environment>" npm run test:saleads-mi-negocio
```

Notes:
- The test does not hardcode a specific SaleADS domain.
- If `SALEADS_BASE_URL` is omitted, the browser must already be on the SaleADS login page (otherwise the test fails fast).
- To run headed mode: `HEADLESS=false npm run test:saleads-mi-negocio`.
