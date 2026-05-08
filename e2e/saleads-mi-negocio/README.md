# SaleADS Mi Negocio Full Workflow Test

This folder contains a standalone Playwright test named `saleads_mi_negocio_full_test` that validates the complete Mi Negocio workflow:

1. Login with Google.
2. Expand **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Generate PASS/FAIL report per validation field.

## Environment-agnostic behavior

- No SaleADS domain is hardcoded.
- Use `SALEADS_LOGIN_URL` to target any environment login page (dev, staging, production, etc.).
- If your runtime starts from an already-loaded login page, keep that behavior in your runner and omit the variable.

## Setup

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

```bash
cd /workspace/e2e/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:saleads-mi-negocio
```

Optional environment variables:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` to run headed mode.

## Evidence and report artifacts

Artifacts are saved under:

```text
e2e/saleads-mi-negocio/artifacts/<run-id>/
```

Each run stores:

- Checkpoint screenshots (dashboard, menu, modal, account page, legal pages).
- `final-report.json` with PASS/FAIL status for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
