# SaleADS Mi Negocio full workflow E2E

This directory contains the `saleads_mi_negocio_full_test` Playwright test, which validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal
4. Administrar Negocios account page sections
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones (including new-tab handling)
9. Política de Privacidad (including new-tab handling)
10. Final PASS/FAIL report per required field

## Environment-agnostic behavior

- No SaleADS domain is hardcoded.
- If the test starts at `about:blank`, provide one of:
  - `SALEADS_LOGIN_URL`
  - `SALEADS_URL`
  - `BASE_URL`
- If the browser is already on the current environment login page, navigation is not required.

## Run

```bash
npm run test:e2e
```

Run headed:

```bash
npm run test:e2e:headed
```

## Evidence generated during the run

- Screenshots attached at every important checkpoint
- `final-report.json` attachment with PASS/FAIL values for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
- Final URLs captured for legal pages
