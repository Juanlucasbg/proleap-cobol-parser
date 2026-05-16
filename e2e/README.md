# SaleADS Mi Negocio E2E

This folder contains the Playwright automation for:

- `saleads_mi_negocio_full_test`

The test validates the full Mi Negocio workflow after Google login, captures screenshots at key checkpoints, validates legal links (same-tab or new-tab), and writes a final PASS/FAIL report JSON.

## Prerequisites

```bash
cd /workspace/e2e
npm install
npm run install:browsers
```

## Environment variables

- `SALEADS_LOGIN_URL` (required): Login URL for the current SaleADS environment.
  - Example: dev/staging/prod URL supplied at runtime (not hardcoded in test).
- `GOOGLE_ACCOUNT_EMAIL` (optional): Defaults to `juanlucasbarbiergarzon@gmail.com`.
- `HEADLESS` (optional): Set `false` to run headed.

## Run test

```bash
cd /workspace/e2e
SALEADS_LOGIN_URL="https://<current-environment-login>" npm run test:mi-negocio
```

## Output evidence

- Screenshots: `e2e/screenshots/saleads-mi-negocio/`
- Final report: `e2e/test-results/saleads-mi-negocio-report.json`
