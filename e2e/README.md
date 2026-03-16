# SaleADS Mi Negocio - Full Workflow E2E

This folder contains an environment-agnostic Playwright test for the workflow:

- Login with Google
- Navigate to **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Open and validate **Administrar Negocios**
- Validate sections:
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
- Validate legal links:
  - Términos y Condiciones
  - Política de Privacidad
- Produce final PASS/FAIL report as test attachment

## Prerequisites

- Node.js 18+
- Playwright browsers installed

## Install

```bash
cd e2e
npm install
npx playwright install
```

## Run

```bash
cd e2e
SALEADS_LOGIN_URL="https://<your-environment-login-page>" npm test
```

### Optional environment variables

- `SALEADS_LOGIN_URL`: login page URL for current environment (dev/staging/prod).
- `GOOGLE_ACCOUNT_EMAIL`: Google account to select if account chooser appears.
  - Default: `juanlucasbarbiergarzon@gmail.com`
- `PW_HEADLESS`: set `false` to run headed.

## Evidence produced by the test

The test captures screenshots at key checkpoints and attaches:

- Dashboard loaded
- Mi Negocio menu expanded
- Crear Nuevo Negocio modal
- Administrar Negocios full page
- Términos y Condiciones page
- Política de Privacidad page
- Final JSON report with PASS/FAIL per required step
