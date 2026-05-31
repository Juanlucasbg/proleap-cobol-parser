# SaleADS.ai E2E tests

This folder contains Playwright-based E2E automation for the SaleADS.ai
"Mi Negocio" workflow.

## Setup

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Run

The test is environment-agnostic and does not hardcode any domain.
Provide the login page URL for the target environment:

```bash
cd e2e
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:saleads-mi-negocio
```

Alternative variables accepted by the test:

- `BASE_URL`
- `PLAYWRIGHT_BASE_URL`

## Outputs

- HTML report under `playwright-report/`
- Final JSON pass/fail report attached in test output as:
  `saleads-mi-negocio-final-report.json`
- Checkpoint screenshots captured during key workflow steps.
