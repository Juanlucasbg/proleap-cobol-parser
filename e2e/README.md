# SaleADS E2E tests

This folder contains Playwright tests for SaleADS.ai user workflows.

## Install

```bash
npm install
npx playwright install
```

## Run the Mi Negocio workflow test

```bash
BASE_URL="https://your.saleads.environment/login" \
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npm run test:saleads
```

## Notes

- The test is environment-agnostic and can use `BASE_URL` or `SALEADS_URL`.
- It starts on the current environment login page and continues beyond login through Mi Negocio validations.
- Screenshots and traces are saved under `playwright-report/` and `test-results/`.
