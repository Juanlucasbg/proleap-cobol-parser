# SaleADS Mi Negocio Full Workflow Test

This Playwright test automates the `saleads_mi_negocio_full_test` workflow:

1. Login with Google
2. Open **Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Generate a final PASS/FAIL report

## Environment-agnostic behavior

- No domain is hardcoded.
- Provide the login page URL for the current environment via env var.

## Required environment variables

- `SALEADS_LOGIN_URL`: Login URL of the target SaleADS environment (dev/staging/prod).

## Optional environment variables

- `HEADLESS=false` to run headed.

## Run

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps
npm run test:saleads-mi-negocio
```

Example:

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:saleads-mi-negocio
```

## Evidence artifacts

Generated under `qa/saleads-mi-negocio/artifacts/`:

- `01-dashboard-loaded.png`
- `02-mi-negocio-menu-expanded.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios-page-full.png`
- `05-terminos-y-condiciones.png`
- `06-politica-de-privacidad.png`
- `final-report.json`
