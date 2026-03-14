# SaleADS Mi Negocio full workflow test

This Playwright test automates the complete `saleads_mi_negocio_full_test` flow:

1. Login with Google.
2. Open and validate `Mi Negocio` menu.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (including new-tab handling).
9. Validate `Política de Privacidad` (including new-tab handling).
10. Generate final PASS/FAIL report.

## Environment agnostic setup

Set one of these variables to the login page of the target environment:

- `SALEADS_LOGIN_URL`
- `SALEADS_URL`
- `SALEADS_BASE_URL`
- `BASE_URL`

No domain is hardcoded in the test.

## Run

```bash
npm run test:e2e
```

Optional headed run:

```bash
npm run test:e2e:headed
```

## Evidence

The test captures screenshots at key checkpoints and attaches:

- Dashboard after login
- Mi Negocio expanded menu
- Agregar Negocio modal
- Administrar Negocios view
- Términos y Condiciones page
- Política de Privacidad page
- Final JSON report with PASS/FAIL per requested section and legal URLs
