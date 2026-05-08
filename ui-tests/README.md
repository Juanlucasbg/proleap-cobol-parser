# SaleADS UI automation

This folder contains a standalone Playwright test suite for SaleADS workflows.

## Test included

- `tests/saleads_mi_negocio_full_test.spec.js`

## Run

```bash
cd ui-tests
npm install
npx playwright install chromium
npm run test:saleads-mi-negocio
```

## Environment variables

- `SALEADS_URL` (optional): If set, the test opens this URL first.
  - If not set, the test expects the browser to already be on the SaleADS login page.
- `SALEADS_ARTIFACTS_DIR` (optional): Folder for screenshots/report.
  - Default: `ui-tests/artifacts`
- `PW_HEADLESS` (optional): Set to `false` to run headed.

## Evidence generated

- Checkpoint screenshots:
  - `ui-tests/artifacts/screenshots/saleads_mi_negocio_full_test/`
- Final validation report:
  - `ui-tests/artifacts/saleads_mi_negocio_full_test.report.json`
