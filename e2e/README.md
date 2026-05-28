# SaleADS E2E automation

This folder contains a Playwright workflow test for:

- **saleads_mi_negocio_full_test**

The test is written to be **environment-agnostic**. It does not hardcode a SaleADS domain and reads the login URL from environment variables.

## Prerequisites

- Node.js 20+ (recommended)
- npm

## Install dependencies

```bash
cd e2e
npm install
npm run pw:install
```

## Run the test

Set the login page URL for your current environment (dev/staging/prod) and run:

```bash
cd e2e
SALEADS_LOGIN_URL="https://<your-current-saleads-login-url>" npm run test:saleads-mi-negocio
```

If your environment already injects `SALEADS_URL`, `APP_URL`, or `BASE_URL`, the test can use those too.

## Artifacts

After execution, the test writes evidence into:

- `e2e/artifacts/saleads_mi_negocio_full_test/`
  - checkpoint screenshots
  - `final-report.json` with PASS/FAIL by requested report field
  - captured final legal URLs

It also generates Playwright reports in:

- `e2e/artifacts/html-report/`
- `e2e/artifacts/playwright-report.json`
