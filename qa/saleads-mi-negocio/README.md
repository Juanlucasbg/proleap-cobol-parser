# SaleADS Mi Negocio Full Workflow Test

This folder contains a standalone Playwright end-to-end test for:

- Google login flow
- Mi Negocio sidebar expansion
- Agregar Negocio modal validation
- Administrar Negocios account view validation
- Información General, Detalles de la Cuenta, and Tus Negocios validations
- Términos y Condiciones + Política de Privacidad link handling (new tab or same tab)
- Checkpoint screenshots and a machine-readable final PASS/FAIL report

## Why this test is environment-agnostic

- It never hardcodes any SaleADS domain.
- You provide the login page URL at runtime with `SALEADS_LOGIN_URL`.
- Selectors prioritize visible text in Spanish (and login fallbacks for English).

## Prerequisites

- Node.js 18+
- Playwright browsers installed:

```bash
npx playwright install chromium
```

## Run

From this folder:

```bash
export SALEADS_LOGIN_URL="https://<current-saleads-environment>/login"
npm test
```

Optional:

```bash
export GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"
npm run test:headed
```

## Output artifacts

- Screenshots at important checkpoints in Playwright output directories.
- `final-report.json` attached to the test result with PASS/FAIL by:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
