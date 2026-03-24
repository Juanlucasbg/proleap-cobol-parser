# SaleADS QA Automation

This folder contains a standalone Playwright suite for validating the **Mi Negocio** workflow in any SaleADS.ai environment.

## Test included

- `saleads_mi_negocio_full_test`
  - Logs in with Google (and handles Google account selection when shown)
  - Continues through Mi Negocio module checks (menu, modal, account sections, legal links)
  - Captures checkpoint screenshots
  - Produces a final JSON report with PASS/FAIL per required validation field

## Prerequisites

- Node.js 20+ (Node 22 recommended)
- A reachable SaleADS login URL (do not hardcode in test code; pass via env variable)

## Install

```bash
cd qa
npm install
npx playwright install chromium
```

## Run

Set the environment URL at runtime:

```bash
cd qa
SALEADS_BASE_URL="https://<current-saleads-env>" npm run test:mi-negocio
```

Or use:

```bash
BASE_URL="https://<current-saleads-env>" npm run test:mi-negocio
```

## Artifacts

- Screenshots: `qa/artifacts/screenshots/`
- Final reports: `qa/artifacts/reports/`
- Playwright json report: `qa/artifacts/playwright-report.json`

## Notes

- Selectors prioritize visible text and accessible roles.
- The legal links support both same-tab navigation and new-tab behavior.
- If no base URL is provided, the test expects the browser session to already be at the SaleADS login page.
