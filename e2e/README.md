# SaleADS E2E: Mi Negocio Full Workflow

This folder contains the Playwright automation for:

- `saleads_mi_negocio_full_test`

It validates the complete "Mi Negocio" workflow after Google login, including legal links, tab handling, and screenshot evidence.

## Environment compatibility

The test does **not** hardcode any SaleADS domain.

- If your runner already starts on the SaleADS login page, no URL is required.
- If it starts on a blank page, pass `SALEADS_START_URL` for your current environment.

## Install and run

```bash
cd e2e
npm install
npx playwright install --with-deps
npm test
```

Optional:

```bash
SALEADS_START_URL="https://your-env-url/login" npm test
HEADLESS=false npm run test:headed
```

## Evidence and report

The test captures checkpoint screenshots and writes a final JSON report with PASS/FAIL by step:

- `test-results/.../checkpoints/*.png`
- `test-results/.../final-report.json`
