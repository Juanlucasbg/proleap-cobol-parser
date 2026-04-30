# SaleADS E2E tests

This folder contains browser automation for SaleADS workflows.

## Scenario implemented

- `saleads-mi-negocio-full-test.spec.js`
  - Logs in with Google from the current environment login page (no hardcoded domain).
  - Validates the complete Mi Negocio module workflow.
  - Captures screenshots at required checkpoints.
  - Handles legal links that may open in the same tab or a new tab.
  - Produces a final PASS/FAIL report for every requested section.

## Prerequisites

- Node.js 18+ and npm.
- Browser already on a SaleADS login page from any environment.
- Optionally set:
  - `SALEADS_BASE_URL` to open a login page automatically.
  - `SALEADS_GOOGLE_EMAIL` (defaults to `juanlucasbarbiergarzon@gmail.com`).

## Install

```bash
cd /workspace/e2e
npm install
npm run install:browsers
```

## Run

Headless:

```bash
npm run test:mi-negocio
```

Headed:

```bash
npm run test:mi-negocio:headed
```

If `SALEADS_BASE_URL` is not provided, the test expects the browser to already be on a SaleADS login page of the current environment.

## Evidence

Screenshots and Playwright artifacts are stored under:

- `e2e/artifacts/`
- `e2e/test-results/`
- `e2e/playwright-report/`
