# SaleADS.ai - Mi Negocio full workflow test

This folder contains a Playwright E2E test that validates the complete **Mi Negocio** flow:

1. Login with Google
2. Open **Negocio > Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones** (same tab or new tab)
9. Validate **Política de Privacidad** (same tab or new tab)
10. Emit a final PASS/FAIL report

The test is intentionally environment-agnostic:

- No domain is hardcoded.
- It reads the login page URL from env vars.
- It uses selectors by visible text whenever possible.

## Prerequisites

- Node.js 18+ (recommended)
- Chromium browser for Playwright

Install dependencies and browser:

```bash
cd e2e
npm install
npm run install:browsers
```

## Environment variables

- `SALEADS_LOGIN_URL` (preferred): Full login URL for the target environment.
- `SALEADS_BASE_URL` (fallback): Used when `SALEADS_LOGIN_URL` is not provided.
- `SALEADS_GOOGLE_ACCOUNT` (optional): Google account email to select.  
  Default: `juanlucasbarbiergarzon@gmail.com`
- `SALEADS_UI_WAIT_MS` (optional): Extra wait after each click.  
  Default: `1200`
- `HEADLESS` (optional): Set to `false` to run headed.

## Run

```bash
cd e2e
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:saleads-mi-negocio
```

Headed mode:

```bash
cd e2e
HEADLESS=false SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:saleads-mi-negocio:headed
```

## Evidence generated

The test captures screenshots at key checkpoints:

- Dashboard after login
- Mi Negocio menu expanded
- Agregar Negocio modal
- Administrar Negocios page
- Términos y Condiciones
- Política de Privacidad

It also writes a JSON report with PASS/FAIL per requested validation field:

- `saleads-mi-negocio-report.json` (inside the Playwright test output folder)
