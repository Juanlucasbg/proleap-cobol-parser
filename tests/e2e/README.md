# SaleADS Mi Negocio E2E

This directory contains the Playwright scenario `saleads_mi_negocio_full_test` for validating the full Mi Negocio workflow.

## Run

```bash
npx playwright install
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm run test:e2e -- tests/e2e/saleads-mi-negocio-full-test.spec.js
```

If your execution environment already opens the SaleADS login page before the test starts, you can omit `SALEADS_LOGIN_URL`.

## Evidence

The test captures screenshots at key checkpoints and attaches a JSON report (`mi-negocio-final-report.json`) with PASS/FAIL status per requested validation area and legal-page final URLs.
