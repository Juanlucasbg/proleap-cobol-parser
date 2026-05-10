# SaleADS QA Workflows

This folder contains browser automation tests for SaleADS workflows using Playwright.

## Test included

- `tests/saleads-mi-negocio-full.spec.js`
  - Covers Google login and complete **Mi Negocio** workflow validation.
  - Captures screenshots at key checkpoints.
  - Produces a JSON report with PASS/FAIL status for each requested section.
  - Handles both same-tab and popup behavior for legal links.

## Environment-agnostic execution

No SaleADS domain is hardcoded in the test.

Set the login URL at runtime according to the environment you want to validate:

```bash
cd /workspace/qa
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm test
```

You can also run headed mode:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm run test:headed
```

## Evidence and outputs

- Screenshots and report are written to Playwright output directories under:
  - `test-results/`
  - `playwright-report/` (HTML report)
- Final PASS/FAIL report filename:
  - `saleads_mi_negocio_full_report.json`
