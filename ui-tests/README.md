# SaleADS UI tests

This folder contains Playwright end-to-end coverage for the SaleADS **Mi Negocio** workflow.

## Test included

- `tests/saleads_mi_negocio_full_test.spec.js`

This test validates:

1. Login with Google.
2. Sidebar expansion for `Negocio > Mi Negocio`.
3. `Agregar Negocio` modal fields and actions.
4. `Administrar Negocios` account view sections.
5. `Información General` fields.
6. `Detalles de la Cuenta` fields.
7. `Tus Negocios` fields.
8. `Términos y Condiciones` (same tab or new tab).
9. `Política de Privacidad` (same tab or new tab).
10. Final PASS/FAIL report for each requested validation domain.

Screenshots are captured at important checkpoints and stored in Playwright test output.

## Environment-agnostic execution

The test does not rely on a hardcoded domain. Provide the target environment at runtime:

- `SALEADS_URL`: login page URL for the current environment.
- `SALEADS_SKIP_NAVIGATION=true`: skip `page.goto()` when your runner already opens the login page.
- `SALEADS_EXPECTED_USER_NAME` (optional): strict assertion for profile name.
- `SALEADS_EXPECTED_USER_EMAIL` (optional): strict assertion for profile email.

## Install

```bash
cd ui-tests
npm install
npx playwright install chromium
```

## Run

```bash
cd ui-tests
SALEADS_URL="https://<your-saleads-environment>/login" npm run test:saleads-mi-negocio
```

Headed mode:

```bash
cd ui-tests
HEADED=true SALEADS_URL="https://<your-saleads-environment>/login" npm run test:saleads-mi-negocio
```
