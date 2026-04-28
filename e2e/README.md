# SaleADS E2E Automation

This folder contains a Playwright end-to-end automation for:

- `saleads_mi_negocio_full_test`

The test validates the complete **Mi Negocio** workflow, not only login.

## What the test covers

1. Login with Google (including account selector when shown).
2. Sidebar validation and **Mi Negocio** menu expansion.
3. **Agregar Negocio** modal validation.
4. **Administrar Negocios** view validation.
5. **Informacion General** section validation.
6. **Detalles de la Cuenta** section validation.
7. **Tus Negocios** section validation.
8. **Terminos y Condiciones** validation (same tab or popup).
9. **Politica de Privacidad** validation (same tab or popup).
10. Final PASS/FAIL JSON report generation per required field.

The test also captures screenshots at important checkpoints.

## Environment-agnostic design

This automation is portable across SaleADS environments (dev/staging/prod):

- It does **not** hardcode any domain.
- It uses visible text-first locators.
- It supports legal links opening in the same tab or a new tab.

## Prerequisites

- Node.js 18+ recommended.
- Playwright browsers installed.

## Install

```bash
cd /workspace/e2e
npm install
npx playwright install
```

## Run

If browser is already at login page, run headed mode and start there manually:

```bash
cd /workspace/e2e
npm run test:mi-negocio:headed
```

If you want the test to navigate automatically:

```bash
cd /workspace/e2e
SALEADS_LOGIN_URL="https://your-saleads-login-url" npm run test:mi-negocio:headed
```

You can also use:

```bash
SALEADS_BASE_URL="https://your-saleads-base-url" npm run test:mi-negocio:headed
```

## Optional environment variables

- `SALEADS_LOGIN_URL`: explicit login URL.
- `SALEADS_BASE_URL`: base app URL if login URL is not provided.
- `GOOGLE_ACCOUNT_EMAIL`: Google account to select.  
  Default: `juanlucasbarbiergarzon@gmail.com`
- `PW_HEADLESS`: set `false` to run headed.

## Outputs

- Screenshots: `e2e/artifacts/screenshots/`
- Final report JSON: `e2e/artifacts/reports/`

Each report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
