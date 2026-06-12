# SaleADS Mi Negocio full workflow test

This folder contains the automated E2E test `saleads_mi_negocio_full_test` implemented with Playwright.

## What it validates

The test covers the full flow requested for the **Mi Negocio** module:

1. Login with Google (including optional account selection for `juanlucasbarbiergarzon@gmail.com`)
2. Open **Negocio > Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones** (including new-tab handling)
9. Validate **Política de Privacidad** (including new-tab handling)
10. Generate a final PASS/FAIL report as test attachment

Screenshots are captured at the required checkpoints and attached to the test output.

## Environment-agnostic behavior

- No specific SaleADS domain is hardcoded.
- If the browser context already starts at the login page, the test continues from there.
- If the test starts on `about:blank`, provide `SALEADS_LOGIN_URL` at runtime.

## Run locally

```bash
npm install
npx playwright install chromium
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:e2e:saleads
```

Headed mode:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:e2e:saleads:headed
```
