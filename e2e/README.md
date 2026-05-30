# SaleADS Mi Negocio E2E

This directory contains the Playwright suite for the workflow:

- Login with Google
- Open `Mi Negocio`
- Validate `Agregar Negocio` modal
- Validate `Administrar Negocios` sections
- Validate legal links:
  - `Términos y Condiciones`
  - `Política de Privacidad`

## Why this works across environments

- No hardcoded SaleADS domain is used.
- The target login page is passed with environment variables.
- Selectors are primarily text-based and include Spanish + fallback variants.

## Required environment variables

At least one of:

- `SALEADS_LOGIN_URL`
- `SALEADS_URL`
- `BASE_URL`
- `TARGET_URL`

Optional:

- `GOOGLE_ACCOUNT_EMAIL` (defaults to `juanlucasbarbiergarzon@gmail.com`)
- `GOOGLE_PASSWORD` (only used if Google asks for a password input in the test flow)
- `USE_CURRENT_PAGE=true` (skip `page.goto` when the login page is preloaded by an external runner)

## Install and run

From repository root:

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://your-env.saleads.ai/login" npm test
```

## Artifacts

The run stores evidence and report in:

- `e2e/test-results/saleads_mi_negocio_full_test/screenshots/`
- `e2e/test-results/saleads_mi_negocio_full_test/report.json`

`report.json` includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
