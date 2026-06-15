# SaleADS Mi Negocio Full Workflow Test

This repository now includes an environment-agnostic Playwright E2E test:

- `tests/saleads_mi_negocio_full_test.spec.js`

## What it validates

The test covers the full requested flow:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones
9. Validate Politica de Privacidad
10. Emit a final PASS/FAIL report per section

Screenshots are attached as test artifacts at each key checkpoint.

## How to run

Install browsers once:

```bash
npm run e2e:install
```

Run the workflow test (headless):

```bash
SALEADS_LOGIN_URL="https://<current-env-login-page>" npm run e2e:saleads
```

Run headed:

```bash
SALEADS_LOGIN_URL="https://<current-env-login-page>" npm run e2e:saleads:headed
```

## Environment agnostic behavior

The test never hardcodes a domain. It reads the login page URL from one of:

- `SALEADS_LOGIN_URL`
- `SALEADS_BASE_URL`
- `BASE_URL`
- `PLAYWRIGHT_BASE_URL`

If none is set, Playwright marks the test as skipped with guidance.
