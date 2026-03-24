# SaleADS Mi Negocio E2E test

This repository now includes a Playwright end-to-end test for the full "Mi Negocio" workflow.

## Run

1. Install dependencies:

```bash
npm install
npx playwright install --with-deps chromium
```

2. Ensure the browser is on the SaleADS login page in your target environment (dev/staging/prod), then run:

```bash
npm run test:e2e:saleads-mi-negocio
```

## Environment variables

- `GOOGLE_ACCOUNT_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.

## Outputs

- Checkpoint screenshots: `artifacts/saleads_mi_negocio_full_test/`
- Structured final report: `artifacts/saleads_mi_negocio_full_test/final-report.json`

