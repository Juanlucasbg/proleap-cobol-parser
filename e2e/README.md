# SaleADS E2E automation

This folder contains an environment-agnostic Playwright test for the full **Mi Negocio** workflow:

- Test name: `saleads_mi_negocio_full_test`
- File: `tests/saleads_mi_negocio_full_test.spec.js`

## Prerequisites

1. Install dependencies:

   ```bash
   npm install
   ```

2. Install browser binaries:

   ```bash
   npx playwright install chromium
   ```

## Execute the workflow test

Set the SaleADS login page URL for the current environment (`dev`, `staging`, `production`, etc.) and run:

```bash
SALEADS_LOGIN_URL="https://<current-saleads-login-url>" npm run test:saleads-mi-negocio
```

Optional:

- Override Google account selector target:

  ```bash
  SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com"
  ```

## Evidence and report

Run artifacts are generated under:

- `artifacts/saleads_mi_negocio_full_test/<runStamp>/screenshots`
- `artifacts/saleads_mi_negocio_full_test/<runStamp>/saleads_mi_negocio_full_test.report.json`

The JSON report includes PASS/FAIL status for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
