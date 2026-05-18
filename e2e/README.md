# SaleADS E2E tests

This folder contains a Playwright implementation of the `saleads_mi_negocio_full_test` workflow.

## Setup

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Run

Set an environment URL for the current SaleADS environment login page (dev/staging/prod):

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm run test:saleads-mi-negocio
```

Notes:
- The test does not hardcode any SaleADS domain.
- It prefers selectors by visible text.
- It captures screenshots at key checkpoints and writes a final JSON report with PASS/FAIL per section.
- If legal links open in a new tab, the test validates that tab and then returns to the application.
