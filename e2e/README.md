# SaleADS E2E tests

This folder contains browser automation for the SaleADS "Mi Negocio" module workflow.

## Test included

- `saleads-mi-negocio-full.spec.js`
  - Logs in with Google.
  - Expands `Mi Negocio`.
  - Validates `Agregar Negocio` modal.
  - Opens `Administrar Negocios` and validates all required sections.
  - Validates `Términos y Condiciones` and `Política de Privacidad` (same tab or new tab).
  - Captures screenshots at key checkpoints.
  - Generates a final JSON PASS/FAIL report per requested validation field.

## Environment-agnostic behavior

The test does **not** hardcode a domain. You can run it in dev/staging/prod by either:

1. Setting `SALEADS_LOGIN_URL` (or `BASE_URL`), or
2. Launching the browser already on the SaleADS login page.

## Run

```bash
npm run e2e:install-browsers
npm run test:e2e:saleads-mi-negocio
```

## Optional environment variables

- `SALEADS_LOGIN_URL`: Login page URL for the current environment.
- `BASE_URL`: Alternative login URL variable.
- `SALEADS_GOOGLE_ACCOUNT_EMAIL`: Google account to select in the account chooser.
  - Default: `juanlucasbarbiergarzon@gmail.com`
- `HEADLESS`: Set to `false` to run headed.
- `SALEADS_WAIT_AFTER_CLICK_MS`: UI stabilization wait after clicks (default `900` ms).
