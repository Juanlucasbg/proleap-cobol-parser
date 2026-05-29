# SaleADS.ai E2E Suite

This folder contains Playwright automation for the `saleads_mi_negocio_full_test` workflow.

## Prerequisites

1. Install dependencies:

   ```bash
   npm install
   npm run playwright:install
   ```

2. Provide a login URL for the current SaleADS environment (dev/staging/prod):

   ```bash
   export SALEADS_LOGIN_URL="https://<current-environment>/login"
   ```

   You can also use `SALEADS_BASE_URL` or `BASE_URL`.

## Run the workflow test

```bash
npm run test:saleads-mi-negocio
```

Optional headed run:

```bash
npm run test:headed
```

## Evidence and report

The test captures screenshots at key checkpoints and writes a JSON final report with PASS/FAIL status per requested section. Playwright stores outputs in:

- `test-results/`
- `playwright-report/`
