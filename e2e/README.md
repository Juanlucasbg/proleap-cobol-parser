# SaleADS Mi Negocio E2E

This folder contains the automated workflow test:

- `saleads_mi_negocio_full_test.spec.js`

## Run

1. Install dependencies (already done once if `node_modules` exists):

```bash
npm install
```

2. Install Playwright browsers:

```bash
npx playwright install
```

3. Run the test against any SaleADS environment by providing the login URL via environment variable:

```bash
SALEADS_START_URL="https://<your-saleads-login-url>" npm run test:saleads-mi-negocio
```

Optional headed mode:

```bash
SALEADS_START_URL="https://<your-saleads-login-url>" npm run test:saleads-mi-negocio:headed
```

## Evidence output

The test writes evidence artifacts to:

- `artifacts/saleads_mi_negocio_full_test/`
  - checkpoint screenshots (`.png`)
  - `final-report.json` with PASS/FAIL per required validation plus legal page URLs
