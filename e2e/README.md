# SaleADS.ai E2E automation

This folder contains Playwright browser automation for SaleADS.ai workflows.

## Implemented workflow

- `tests/saleads-mi-negocio-full.spec.js`
  - Logs in with Google
  - Validates the full **Mi Negocio** flow
  - Captures checkpoint screenshots
  - Produces a final PASS/FAIL report attachment

## Environment variables

- `SALEADS_START_URL` (recommended): login page URL for the target environment.
- `SALEADS_BASE_URL` (optional): Playwright base URL; used if `SALEADS_START_URL` is not provided.
- `HEADLESS=false` (optional): run in headed mode.

The test does not hardcode any environment-specific SaleADS domain.

## Run

```bash
cd /workspace/e2e
npm run install:browsers
npm run test:saleads-mi-negocio
```
