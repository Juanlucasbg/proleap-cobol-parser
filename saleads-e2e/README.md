# SaleADS Mi Negocio Full Workflow Test

This folder contains the Playwright automated test `saleads_mi_negocio_full_test` for validating the complete Mi Negocio flow:

1. Login with Google.
2. Expand Mi Negocio menu.
3. Validate Agregar Negocio modal.
4. Validate Administrar Negocios view.
5. Validate Informacion General.
6. Validate Detalles de la Cuenta.
7. Validate Tus Negocios.
8. Validate Terminos y Condiciones.
9. Validate Politica de Privacidad.
10. Emit PASS/FAIL report for all requested fields.

## Requirements

- Node.js 20+ (or compatible current version)
- Access to a SaleADS login URL for the target environment

## Install

```bash
cd saleads-e2e
npm install
npx playwright install --with-deps
```

## Run

Set one environment variable (no hardcoded domain in test code):

```bash
export SALEADS_LOGIN_URL="https://<current-environment-login-page>"
```

Then run:

```bash
npm run test:saleads-mi-negocio
```

## Evidence and report output

- Screenshots are saved in Playwright test output folders, including:
  - dashboard loaded
  - Mi Negocio expanded menu
  - Agregar Negocio modal
  - Administrar Negocios full page
  - Terminos y Condiciones page
  - Politica de Privacidad page
- Final JSON attachment:
  - `saleads-mi-negocio-final-report.json`
  - Includes PASS/FAIL for all report fields and captured legal URLs.

## Notes

- The test prefers visible-text selectors per workflow requirement.
- If legal links open in new tabs, the test validates content there and returns to the application tab automatically.
- If Google account chooser appears, the test selects:
  - `juanlucasbarbiergarzon@gmail.com`
