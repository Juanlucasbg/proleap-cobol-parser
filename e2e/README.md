# SaleADS E2E Tests

This folder contains browser automation tests for SaleADS workflows using Playwright.

## Setup

```bash
cd e2e
npm install
npm run install:browsers
```

## Run the Mi Negocio full workflow test

Set one of these environment variables to point to the login page of the current environment:

- `SALEADS_LOGIN_URL`
- `SALEADS_URL`
- `BASE_URL`

Then run:

```bash
cd e2e
SALEADS_LOGIN_URL="https://your-saleads-environment/login" npm run test:saleads-mi-negocio
```

## Artifacts

- Playwright output: `e2e/playwright-report` and `e2e/test-results`
- Latest JSON report: `e2e/artifacts/saleads-mi-negocio-latest-report.json`
