# SaleADS UI Tests

This folder contains environment-agnostic Playwright tests for SaleADS workflows.

## Test included

- `saleads_mi_negocio_full_test`  
  Full workflow: login with Google and validate the Mi Negocio module, including legal links, evidence screenshots, and PASS/FAIL report fields.

## Prerequisites

- Node.js 18+ (or newer)
- A valid SaleADS login URL for the target environment (dev/staging/prod)

## Install

```bash
npm install
npx playwright install
```

## Run

Use the current environment login URL (no hardcoded domain):

```bash
SALEADS_URL="https://<current-env-login-url>" npm run test:saleads-mi-negocio
```

Or run headed:

```bash
SALEADS_URL="https://<current-env-login-url>" npm run test:headed -- tests/saleads_mi_negocio_full_test.spec.js
```

## Output artifacts

Playwright stores results under `test-results/`.  
This test writes:

- checkpoint screenshots for required milestones
- `final-report.json` with PASS/FAIL by section and captured legal URLs
