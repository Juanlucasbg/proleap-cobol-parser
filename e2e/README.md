# SaleADS Mi Negocio full workflow test

This folder contains a standalone Playwright workflow for:

1. Logging in with Google.
2. Navigating to **Mi Negocio**.
3. Validating modal, account sections, and legal links.
4. Capturing screenshots and generating a per-step PASS/FAIL report.

## Requirements

- Node.js 20+ (tested with Node 22)
- Playwright Chromium browser binaries

## Install

```bash
cd e2e
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://your-current-saleads-login-url" npm run saleads:mi-negocio:full-test
```

Optional environment variables:

- `SALEADS_BASE_URL` or `SALEADS_URL`: fallback login URL variables.
- `HEADLESS=false`: run with visible browser.

## Outputs

For each execution, the script writes:

- Screenshots under `e2e/artifacts/<timestamp>/`
- JSON report at `e2e/artifacts/<timestamp>/final-report.json`

The report contains one PASS/FAIL status entry for each required validation field.
