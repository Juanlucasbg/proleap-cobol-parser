# SaleADS E2E - Mi Negocio Workflow

This folder contains a Playwright test that validates the full **Mi Negocio** workflow:

- Login with Google
- Open **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios**
- Validate:
  - **Informacion General**
  - **Detalles de la Cuenta**
  - **Tus Negocios**
  - **Terminos y Condiciones**
  - **Politica de Privacidad**

The test is intentionally environment-agnostic and does not hardcode any SaleADS domain.

## Test file

- `tests/saleads_mi_negocio_full_test.spec.ts`

## Run locally

```bash
cd e2e
npm install
npm run install:browsers
SALEADS_URL="<current-saleads-login-url>" npm run test:saleads-mi-negocio
```

> If `SALEADS_URL` is not provided, the test assumes the browser is already on the SaleADS login page and fails when it starts on a blank page.

## Evidence and report

The test captures screenshots at checkpoints and writes a structured report as a test attachment:

- `final-report.json`

The report includes:

- PASS/FAIL for each required validation field
- Captured final URLs for legal pages
- Any step failure reasons
