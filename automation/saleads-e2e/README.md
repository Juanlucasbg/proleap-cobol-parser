# SaleADS Mi Negocio Full Workflow Test

This folder contains the Playwright test `saleads_mi_negocio_full_test` that validates:

1. Google login flow
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios sections
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones legal page
9. Política de Privacidad legal page
10. Final PASS/FAIL report

## Environment-agnostic behavior

- The test does **not** hardcode any SaleADS domain.
- Provide one of these environment variables:
  - `SALEADS_LOGIN_URL`
  - `SALEADS_BASE_URL`

## Run

```bash
cd automation/saleads-e2e
npm install
npx playwright install --with-deps chromium
npm test
```

### Headed mode

```bash
npm run test:headed
```

## Artifacts

Playwright output includes:

- checkpoint screenshots for dashboard/menu/modal/account/legal pages
- final URLs for legal pages
- `final-report.json` with PASS/FAIL fields:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
