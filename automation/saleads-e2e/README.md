# SaleADS E2E automation

This folder contains Playwright tests for SaleADS workflows.

## Implemented test

- `saleads_mi_negocio_full_test`

## Environment compatibility

The test is environment-agnostic:

- It does **not** hardcode a domain.
- It accepts either:
  - `SALEADS_LOGIN_URL` (preferred), or
  - `SALEADS_BASE_URL`.
- If neither variable is set, the test expects the browser to already be on the login page.

## Run locally

```bash
cd automation/saleads-e2e
npm install
npx playwright install --with-deps chromium
npm run test:saleads-mi-negocio
```

Optional headed run:

```bash
npm run test:saleads-mi-negocio:headed
```

## Notes

- The scenario validates login and continues through the full "Mi Negocio" flow.
- It captures screenshots at key checkpoints.
- It handles legal links that either navigate the same tab or open a new tab.
- It emits a JSON final report with PASS/FAIL per required block.
