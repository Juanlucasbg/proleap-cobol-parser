# SaleADS Mi Negocio workflow test

This directory contains an environment-agnostic Playwright workflow for:

1. Logging into SaleADS with Google.
2. Validating the full **Mi Negocio** module flow.
3. Capturing screenshot evidence on key checkpoints.
4. Producing a PASS/FAIL report for all requested validations.

## Requirements

- Node.js 18+
- Network access to the target SaleADS environment
- A valid authentication path for Google login (`juanlucasbarbiergarzon@gmail.com` by default)

## Install

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

### Option A: Direct URL (recommended for automation)

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:workflow
```

### Option B: Existing browser session

If a browser is already open on the login page, connect through a Chromium CDP endpoint:

```bash
SALEADS_BROWSER_WS_ENDPOINT="ws://127.0.0.1:<port>/devtools/browser/<id>" npm run test:workflow
```

## Environment variables

- `SALEADS_LOGIN_URL`: Login URL for the current environment (no hardcoded domain in test logic).
- `SALEADS_BROWSER_WS_ENDPOINT`: Optional CDP endpoint for an already opened browser tab.
- `SALEADS_GOOGLE_ACCOUNT`: Defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_HEADLESS`: `true` (default) or `false`.
- `SALEADS_TIMEOUT_MS`: Per-action timeout in milliseconds (default: `20000`).
- `SALEADS_OUTPUT_DIR`: Custom output folder for artifacts.

## Output

Each run writes artifacts to:

`qa/saleads-mi-negocio/artifacts/<timestamp>/`

- `report.json`: Structured execution and validation results.
- `report.md`: Human-readable final report.
- `screenshots/*.png`: Evidence images for major checkpoints and failures.
