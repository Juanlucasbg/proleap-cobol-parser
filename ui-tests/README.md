# SaleADS UI tests (cross-environment)

This folder contains Playwright end-to-end tests for SaleADS workflows.

## Implemented test

- `tests/saleads_mi_negocio_full_test.spec.js`
  - Logs in with Google (supports popup or same-tab flow)
  - Validates complete **Mi Negocio** workflow
  - Captures screenshots at required checkpoints
  - Handles legal links opening in same tab or new tab
  - Writes a final JSON report with PASS/FAIL per required section

## Requirements

- Node.js 18+ recommended
- Chromium browser for Playwright
- Environment variable:
  - `SALEADS_BASE_URL`: login page URL for the current SaleADS environment (dev/staging/prod)

## Install

```bash
cd ui-tests
npm install
npm run pw:install
```

## Run only this workflow

```bash
cd ui-tests
SALEADS_BASE_URL="https://<current-saleads-environment-login>" npm run test:saleads-mi-negocio
```

## Artifacts

- Screenshots: `ui-tests/screenshots/`
- Final report: `ui-tests/artifacts/saleads_mi_negocio_report.json`
- Playwright HTML report: `ui-tests/playwright-report/`
