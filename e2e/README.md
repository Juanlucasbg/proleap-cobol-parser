# SaleADS E2E tests

This folder contains browser-based end-to-end automation for SaleADS workflows.

## Implemented test

- `saleads_mi_negocio_full_test`
  - File: `tests/saleads-mi-negocio-full.spec.js`
  - Covers Google login and full **Mi Negocio** workflow validation
  - Captures screenshots at required checkpoints
  - Handles legal-link behavior for either same-tab navigation or popup tabs
  - Writes a final PASS/FAIL report JSON

## Environment-agnostic execution

The test does **not** hardcode any SaleADS domain. Provide the environment login page through:

- `SALEADS_LOGIN_URL` (required when starting from a fresh Playwright page)

Example values:
- `https://<dev-host>/login`
- `https://<staging-host>/login`
- `https://<production-host>/login`

## Run

```bash
cd e2e
npm install
npm run playwright:install
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm run test:saleads-mi-negocio
```

For headed execution:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm run test:saleads-mi-negocio:headed
```

## Artifacts

After execution, outputs are generated in `e2e/artifacts/`:

- `screenshots/*.png`
- `saleads_mi_negocio_full_test_report.json` (final PASS/FAIL report fields)
- `playwright-run-report.json`
- `test-results/` (Playwright traces/videos on failure)
