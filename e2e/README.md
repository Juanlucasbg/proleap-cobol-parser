# SaleADS Mi Negocio Full Workflow Test

This directory contains the automated Playwright test:

- `saleads_mi_negocio_full_test.spec.js`

The test validates the full requested workflow:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones (including URL capture and tab handling)
9. Validate Politica de Privacidad (including URL capture and tab handling)
10. Produce final PASS/FAIL report per step

## Environment-agnostic behavior

The test does **not** hardcode a SaleADS domain.

Provide the login page for the target environment using `SALEADS_LOGIN_URL`.

## Required environment variables

- `SALEADS_LOGIN_URL` (required): login page URL of the active SaleADS environment.

## Optional environment variables

- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_HEADLESS` (`false` to run headed, defaults to headless)
- `SALEADS_SCREENSHOT_DIR` (custom folder for screenshots and final report)

## Run

```bash
cd /workspace/e2e
npm install
npm run install:browsers
SALEADS_LOGIN_URL="https://<env-login-url>" npm run test:saleads-mi-negocio
```

## Artifacts

The test stores evidence in:

- `e2e/artifacts/saleads-mi-negocio-<timestamp>/`

It includes:

- checkpoint screenshots for each major step
- `final-report.txt` with PASS/FAIL status and captured legal URLs
