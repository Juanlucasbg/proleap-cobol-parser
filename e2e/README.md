# SaleADS E2E tests

This folder contains browser automation tests for SaleADS workflows.

## Test included

- `saleads_mi_negocio_full_test`

## Requirements

- Node.js 20+ (Node 22 is supported)
- Google Chrome/Chromium available for Playwright

## Install

```bash
npm install
npx playwright install chromium
```

## Run

If your runner does not start on the SaleADS login page automatically, pass the environment URL at runtime:

```bash
SALEADS_LOGIN_URL="https://<your-env-login-url>" npm test
```

Optional:

- `HEADLESS=false` to run headed mode

Artifacts (screenshots, trace, and final JSON report) are produced by Playwright in the test output folder.
