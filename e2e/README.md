# SaleADS Mi Negocio E2E

This folder contains a Playwright automation for the workflow:

- Login with Google
- Open `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate account sections and legal links
- Attach screenshots and a final PASS/FAIL report

## Prerequisites

- Node.js 18+ (recommended)
- Browser session can start on the SaleADS login page, or provide an environment URL.

## Installation

```bash
cd e2e
npm install
npm run install:browsers
```

## Execution

If the browser should navigate to an environment URL automatically, define one of:

- `SALEADS_URL`
- `SALEADS_BASE_URL`
- `BASE_URL`

Example:

```bash
cd e2e
SALEADS_URL="https://<your-saleads-environment>/login" npm run test:mi-negocio
```

If no URL is provided, the test expects the page to already be on the SaleADS login page.

## Artifacts

- Screenshots: `e2e/artifacts/screenshots/`
- Playwright report: `e2e/playwright-report/`
- Trace/video on failure: `e2e/test-results/`
- Final JSON report is attached by Playwright as `final-report.json`.
