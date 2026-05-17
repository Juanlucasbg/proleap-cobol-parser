# SaleADS Mi Negocio full workflow test

This suite automates the `saleads_mi_negocio_full_test` workflow using Playwright and validates:

1. Google login flow completion.
2. `Mi Negocio` sidebar expansion.
3. `Agregar Negocio` modal fields and actions.
4. `Administrar Negocios` page sections.
5. `Informacion General` validations.
6. `Detalles de la Cuenta` validations.
7. `Tus Negocios` validations.
8. `Terminos y Condiciones` legal link validation (including new tab handling).
9. `Politica de Privacidad` legal link validation (including new tab handling).
10. Final PASS/FAIL report output.

The test is environment-agnostic and does not hardcode any domain.

## Requirements

- Node.js 20+ (or another recent LTS release)
- Access to a SaleADS environment login page

## Install

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps
```

## Run

Set the login page URL for the current environment before running:

```bash
cd qa/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<your-current-saleads-environment>/login" npm test
```

For debugging in headed mode:

```bash
cd qa/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<your-current-saleads-environment>/login" npm run test:headed
```

## Evidence generated

Playwright stores execution artifacts under `playwright-report` and `test-results`, including:

- `01-dashboard-loaded.png`
- `02-mi-negocio-expanded.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios-view.png`
- `05-terminos-y-condiciones.png`
- `06-politica-de-privacidad.png`
- `saleads-mi-negocio-report.json` (final PASS/FAIL report + legal URLs)
