# SaleADS E2E Tests

This directory contains Playwright end-to-end tests for SaleADS workflows.

## Prerequisites

- Node.js 20+
- Access to a SaleADS login page (any environment)
- Browser session capable of Google login

## Setup

```bash
cd /workspace/e2e
npm install
npm run install:browsers
```

## Run the Mi Negocio full workflow test

Provide the current environment login page URL through `SALEADS_LOGIN_URL`.

```bash
SALEADS_LOGIN_URL="https://your-saleads-login-page" npm test -- --grep "saleads_mi_negocio_full_test"
```

For a visible browser run:

```bash
SALEADS_LOGIN_URL="https://your-saleads-login-page" npm run test:headed -- --grep "saleads_mi_negocio_full_test"
```

## Artifacts

- Step screenshots and evidence: `e2e/test-results/`
- HTML report: `e2e/playwright-report/`
- Console includes final PASS/FAIL report for each validation area
