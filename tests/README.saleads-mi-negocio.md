# SaleADS - Mi Negocio full workflow test

This Playwright test automates the workflow requested in `saleads_mi_negocio_full_test`:

- Google login (with optional account selection)
- `Negocio` -> `Mi Negocio` menu validation
- `Agregar Negocio` modal validation + optional input/cancel
- `Administrar Negocios` page validation
- `Información General`, `Detalles de la Cuenta`, and `Tus Negocios` validations
- `Términos y Condiciones` and `Política de Privacidad` validation (same tab or new tab)
- Checkpoint screenshots + final PASS/FAIL JSON summary

## Environment variables

- `SALEADS_LOGIN_URL` (recommended) or `SALEADS_BASE_URL`: Login page URL for the current environment.
- `SALEADS_GOOGLE_ACCOUNT` (optional, default `juanlucasbarbiergarzon@gmail.com`): account selected in Google chooser if shown.
- `SALEADS_EXPECTED_USER_NAME` (optional): strengthens user name validation in `Información General`.

## Run

```bash
npm run test:saleads:mi-negocio
```

For headed debug mode:

```bash
npm run test:saleads:mi-negocio:headed
```

## Evidence and report

Playwright stores artifacts under `test-results/`.  
The final report is attached by the test as `final-report-json` and saved as:

- `saleads-mi-negocio-final-report.json`

It includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
