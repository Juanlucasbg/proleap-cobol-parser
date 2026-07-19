# SaleADS Mi Negocio Full Workflow E2E

This folder contains an end-to-end Playwright test for the full **Mi Negocio** workflow:

1. Login with Google
2. Open and validate the Mi Negocio sidebar menu
3. Validate the "Agregar Negocio" modal
4. Open "Administrar Negocios" and validate account sections
5. Validate legal documents ("Términos y Condiciones" and "Política de Privacidad")
6. Emit a final PASS/FAIL JSON report per required field

## Environment-agnostic behavior

The test does not hardcode any domain. You provide the environment at runtime using one of:

- `SALEADS_LOGIN_URL`
- `SALEADS_BASE_URL`

## Setup

```bash
cd e2e
npm install
npx playwright install
```

## Run

```bash
cd e2e
SALEADS_LOGIN_URL="https://<saleads-environment-login-url>" npm test
```

Optional:

- `UI_SETTLE_MS` (default `900`) to adjust waits after clicks.

## Evidence and outputs

The test captures screenshots for important checkpoints:

- Dashboard after login
- Mi Negocio expanded menu
- Crear Nuevo Negocio modal
- Administrar Negocios full page
- Términos y Condiciones page
- Política de Privacidad page

Outputs:

- HTML report: `e2e/playwright-report/`
- Raw artifacts: `e2e/test-results/`
- `final-report.json` attachment with:
  - PASS/FAIL per required validation field
  - Final URL for legal pages
  - Error details (if any)
