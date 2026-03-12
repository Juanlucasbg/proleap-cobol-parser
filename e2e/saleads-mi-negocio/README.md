# SaleADS Mi Negocio - Full Workflow E2E

This Playwright test implements `saleads_mi_negocio_full_test` and validates the full Mi Negocio workflow:

1. Login with Google.
2. Open and validate Mi Negocio menu.
3. Validate "Agregar Negocio" modal.
4. Open and validate "Administrar Negocios".
5. Validate "Información General".
6. Validate "Detalles de la Cuenta".
7. Validate "Tus Negocios".
8. Validate "Términos y Condiciones" (new tab or same tab).
9. Validate "Política de Privacidad" (new tab or same tab).
10. Generate a final PASS/FAIL report by section.

## Requirements

- Node.js 18+.
- Playwright browsers installed.

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install
```

## Environment Variables

- `SALEADS_LOGIN_URL` (recommended): Login page URL for the current environment.
  - This keeps the test environment-agnostic and avoids hardcoding domains.
- `GOOGLE_ACCOUNT_EMAIL` (optional): Google account email to select.
  - Default: `juanlucasbarbiergarzon@gmail.com`.
- `HEADLESS` (optional): `false` to run headed.

## Run

```bash
cd e2e/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<current-env>/login" npm test
```

Headed mode:

```bash
cd e2e/saleads-mi-negocio
HEADLESS=false SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:headed
```

## Evidence and Report

The test saves evidence in `artifacts/`:

- Checkpoint screenshots (dashboard, menu, modal, account page, legal pages).
- `final-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
