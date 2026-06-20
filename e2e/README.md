# SaleADS Mi Negocio full workflow test

This Playwright test validates the complete **Mi Negocio** workflow after Google login without hardcoding any SaleADS domain.

## Preconditions

- You have valid Google login access to the target SaleADS environment.
- The login page URL for the current environment is provided at runtime.

## Install

```bash
npm install
npx playwright install --with-deps chromium
```

## Run

Set any of the accepted environment variables with the current environment login page:

- `SALEADS_START_URL`
- `SALEADS_URL`
- `E2E_START_URL`

Example:

```bash
SALEADS_START_URL="https://<current-env>/login" npm run test:saleads-mi-negocio
```

## Evidence and report

The test stores screenshots and report artifacts in `test-results/`:

- `step-1-dashboard-loaded.png`
- `step-2-mi-negocio-menu-expanded.png`
- `step-3-agregar-negocio-modal.png`
- `step-4-administrar-negocios-page.png`
- `step-8-terminos-y-condiciones.png`
- `step-9-politica-de-privacidad.png`
- `saleads-mi-negocio-final-report.json`

The JSON report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
