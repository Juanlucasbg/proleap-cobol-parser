# SaleADS Mi Negocio workflow E2E

This repository now includes a Playwright test for the full workflow:

- `tests/saleads_mi_negocio_full_test.spec.js`

## Run

Install dependencies:

```bash
npm install
npx playwright install chromium
```

Run the test (URL from current environment):

```bash
SALEADS_LOGIN_URL="https://<current-saleads-environment>" npm run test:e2e:saleads
```

Optional headed mode:

```bash
SALEADS_LOGIN_URL="https://<current-saleads-environment>" npm run test:e2e:saleads:headed
```

## Output evidence

Screenshots and JSON report are written to:

- `artifacts/saleads_mi_negocio_full_test/`
  - `final-report.json`

Playwright HTML report:

- `playwright-report/index.html`
