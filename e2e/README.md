# SaleADS Mi Negocio E2E workflow

This folder contains a Playwright end-to-end test that validates the full **Mi Negocio** workflow after login with Google.

## Why this is cross-environment

- The test does **not** hardcode a SaleADS domain.
- It works against any environment by providing `SALEADS_BASE_URL`.
- It relies on visible text selectors whenever possible.

## Prerequisites

- Node.js 20+ and npm
- Playwright browser binaries

## Install

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Run

### Run against any environment URL

```bash
cd e2e
SALEADS_BASE_URL="https://your-saleads-environment.example/login" \
npx playwright test tests/saleads-mi-negocio-workflow.spec.ts --project=chromium
```

## Credentials

The flow expects Google login and may require interactive account selection.

Optional environment variables:

- `SALEADS_BASE_URL` (required, login page URL for current environment)
- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `E2E_ARTIFACTS_DIR` (default: `artifacts`)

## Evidence and report artifacts

After execution:

- Screenshots and URL captures: `e2e/artifacts/mi-negocio/`
- Final JSON report: `e2e/artifacts/mi-negocio/final-report.json`
- Playwright HTML report (if enabled/run locally): `e2e/playwright-report/`

The JSON report includes PASS/FAIL per requested section:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
