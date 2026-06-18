# SaleADS Mi Negocio Full Workflow Test

This Playwright suite automates the workflow named `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open and validate the **Mi Negocio** menu.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Produce a final PASS/FAIL report per requested field.

## Why it is environment-agnostic

- No hardcoded SaleADS domain is used.
- The login page URL is injected at runtime through an environment variable.
- Selectors prefer visible text and role-based locators.

## Prerequisites

- Node.js 22+
- Playwright browsers installed

## Install

```bash
cd automation/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

Provide the current environment login URL (dev/staging/prod):

```bash
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:mi-negocio
```

Run in headed mode:

```bash
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:mi-negocio:headed
```

## Artifacts

The run produces:

- Checkpoint screenshots for dashboard/menu/modal/account/legal pages.
- `saleads-mi-negocio-final-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad

All artifacts are stored in Playwright's output directory (`test-results/`).
