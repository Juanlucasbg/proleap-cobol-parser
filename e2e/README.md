# SaleADS E2E Tests

This folder contains standalone Playwright UI tests for SaleADS workflows.

## Implemented test

- `tests/saleads_mi_negocio_full_test.spec.ts`
  - Logs in with Google when needed.
  - Validates Mi Negocio menu workflow end-to-end.
  - Captures checkpoint screenshots.
  - Handles legal links that open in the same tab or a new tab.
  - Produces a final PASS/FAIL JSON report by required section.

## Environment-agnostic behavior

- The test does **not** depend on a fixed domain.
- It assumes the browser starts on a SaleADS login page.
- If the test starts on `about:blank`, set `SALEADS_BASE_URL` to the target environment URL.

## Run

```bash
cd e2e
npm install
npm run install:browsers
npm run test:mi-negocio
```

Optional (if not already on login page in browser context):

```bash
SALEADS_BASE_URL="https://<your-saleads-environment>" npm run test:mi-negocio
```

## Evidence and report output

- Screenshots and report JSON are generated under:
  - `e2e/artifacts/saleads-mi-negocio-<timestamp>/`
- The final structured report file:
  - `final-report.json`

