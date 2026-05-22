# SaleADS UI Tests

Standalone Playwright E2E tests for SaleADS workflows.

## Included test

- `saleads_mi_negocio_full_test`: login with Google and validate the full **Mi Negocio** flow, including legal links and screenshot checkpoints.

## Setup

```bash
cd ui-tests
npm install
npx playwright install --with-deps chromium
```

## Run

The suite is environment-agnostic and never hardcodes a domain.

Use one of these environment variables:

- `SALEADS_LOGIN_URL` (preferred): full login page URL for the target environment.
- `SALEADS_BASE_URL`: alternative base/login URL if your environment uses a direct landing page.

```bash
cd ui-tests
SALEADS_LOGIN_URL="https://<your-env>/login" npm run test:saleads-mi-negocio
```

If the browser context is already preloaded on the SaleADS login page, the test can run without URL variables.

## Artifacts

Playwright output includes:

- checkpoint screenshots (dashboard, menu, modal, account view, legal pages)
- HTML report in `playwright-report/`
- JSON attachment with final PASS/FAIL report and legal URLs

