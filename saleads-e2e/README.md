# SaleADS Mi Negocio E2E

Playwright test that validates the full Mi Negocio workflow, including Google sign-in, menu navigation, modal checks, account sections, legal links, screenshots, and a PASS/FAIL report.

## Requirements

- Node.js 20+ (Node 22 recommended)
- Chromium installed for Playwright

## Install

```bash
cd saleads-e2e
npm install
npm run install:browsers
```

## Run

Set the environment URL dynamically for the target environment (dev/staging/prod):

```bash
cd saleads-e2e
SALEADS_LOGIN_URL="https://<your-environment-login-page>" npm run test:mi-negocio
```

Optional variables:

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` to run headed
- `E2E_EVIDENCE_DIR` to customize screenshot/report output folder

## Outputs

- Screenshots: `test-results/evidence/*.png`
- JSON report: `test-results/evidence/saleads-mi-negocio-report.json`
- Playwright report: `playwright-report/`
