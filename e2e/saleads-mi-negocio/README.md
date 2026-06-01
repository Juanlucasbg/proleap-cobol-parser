# SaleADS - Mi Negocio Full Workflow Test

This Playwright test automates the full `saleads_mi_negocio_full_test` workflow:

1. Login with Google.
2. Expand **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open and validate **Administrar Negocios** account page.
5. Validate:
   - **Información General**
   - **Detalles de la Cuenta**
   - **Tus Negocios**
6. Open and validate legal links:
   - **Términos y Condiciones**
   - **Política de Privacidad**
7. Return a final PASS/FAIL report per validation field.

## Environment-agnostic behavior

- The test does **not** hardcode any SaleADS domain.
- Provide the environment login URL at runtime:
  - `SALEADS_LOGIN_URL`
- Google account used for account chooser (if shown):
  - `GOOGLE_ACCOUNT_EMAIL` (defaults to `juanlucasbarbiergarzon@gmail.com`)

## Install and run

```bash
npm install
npm run install:browsers
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:saleads-mi-negocio
```

To run in headed mode:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:headed -- tests/saleads_mi_negocio_full_test.spec.ts
```

## Evidence output

The test writes:

- Screenshots checkpoint files to `artifacts/screenshots/`
- Final JSON report to `artifacts/saleads-mi-negocio-report.json`

The JSON report includes:

- PASS/FAIL for each required report field
- Captured Terms and Privacy final URLs
- Screenshot paths and step details
