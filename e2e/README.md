# SaleADS E2E workflows

This folder contains Playwright end-to-end tests for SaleADS.ai UI workflows.

## Implemented scenario

- `tests/saleads-mi-negocio-full.spec.js`
  - Logs in with Google.
  - Validates the full **Mi Negocio** workflow.
  - Handles legal links that may open in the same tab or a new tab.
  - Captures screenshots at required checkpoints.
  - Emits a final PASS/FAIL report (JSON attachment in Playwright results).

## Environment-agnostic execution

No SaleADS domain is hardcoded.

You can run against any environment by providing the login page URL at runtime:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm run test:mi-negocio
```

If your runner already opens the browser on the login page, the test can run without `SALEADS_LOGIN_URL`.

## Install and run

```bash
npm install
npx playwright install chromium
npm run test:mi-negocio
```

Headed mode:

```bash
npm run test:mi-negocio:headed
```

## Artifacts

Playwright stores artifacts under `test-results/` and the HTML report under `playwright-report/`.
