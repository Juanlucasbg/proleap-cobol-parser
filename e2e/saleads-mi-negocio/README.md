# saleads_mi_negocio_full_test

Environment-agnostic Playwright end-to-end workflow for validating SaleADS "Mi Negocio" module.

## What this test validates

1. Login with Google (including account picker fallback).
2. Sidebar visibility and dashboard load.
3. "Mi Negocio" menu expansion with:
   - Agregar Negocio
   - Administrar Negocios
4. "Crear Nuevo Negocio" modal fields/buttons and optional input flow.
5. Administrar Negocios account view sections:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
   - Seccion Legal
6. Legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Screenshot evidence for key checkpoints and final PASS/FAIL report.

## Setup

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm test
```

Optional:

```bash
SALEADS_TEST_BUSINESS_NAME="Negocio Prueba Automatizacion" npm test
```

## Notes

- The test does not hardcode a specific SaleADS domain.
- Provide `SALEADS_LOGIN_URL` for dev, staging, or production at runtime.
- Screenshots are stored in Playwright's output folder for the test run.
