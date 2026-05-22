# SaleADS E2E Tests

This folder contains environment-agnostic end-to-end tests for SaleADS.ai.

## Test: Mi Negocio full workflow

The `saleads_mi_negocio_full_test` script automates:

1. Login using Google.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal validation.
4. Administrar Negocios page and section checks.
5. Legal pages (Términos y Condiciones, Política de Privacidad), including new-tab handling.
6. Final PASS/FAIL JSON report generation.

## Prerequisites

- Node.js 18+ (Node 22 recommended)
- Playwright browsers installed

## Install

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Run

Use an environment variable for the login page in the current SaleADS environment:

```bash
cd e2e
SALEADS_LOGIN_URL="https://<current-environment>/login" npm run test:saleads-mi-negocio
```

Alternative env vars accepted by the test: `SALEADS_URL` or `BASE_URL`.

## Evidence and report output

- Screenshots: `e2e/artifacts/saleads-mi-negocio/screenshots/`
- JSON report: `e2e/artifacts/saleads-mi-negocio/final-report.json`
