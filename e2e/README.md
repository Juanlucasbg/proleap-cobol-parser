# SaleADS E2E Automation

This directory contains an environment-agnostic Playwright test for:

- `saleads_mi_negocio_full_test`

## Preconditions

- Browser starts on the SaleADS login page for the current environment.
- The Google account `juanlucasbarbiergarzon@gmail.com` is available in the account selector when prompted.

## Environment variables

- `SALEADS_START_URL` (recommended): login page URL for the current environment.
- `SALEADS_BASE_URL` / `BASE_URL` / `APP_BASE_URL` (optional): alternative base URL variables used by Playwright config.

## Run locally

```bash
cd /workspace/e2e
npm install
npm run install:browsers
npm run test:saleads:mi-negocio
```

## Artifacts

- Step checkpoint screenshots are attached to the Playwright test result.
- Final structured report is written to: `e2e/artifacts/saleads_mi_negocio_full_test.report.json`
