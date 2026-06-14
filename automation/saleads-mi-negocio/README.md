# SaleADS Mi Negocio full workflow test

This folder contains an end-to-end Playwright test that automates:

1. Google login
2. Sidebar navigation to `Negocio` -> `Mi Negocio`
3. `Agregar Negocio` modal validation
4. `Administrar Negocios` page validation
5. Legal links validation (`Términos y Condiciones`, `Política de Privacidad`)
6. Final PASS/FAIL report generation

## Why this is environment-agnostic

- No domain is hardcoded.
- The test uses `SALEADS_LOGIN_URL` or `SALEADS_BASE_URL` from environment variables.
- If the page is already open through an external harness and not `about:blank`, the test continues from current URL.
- Selectors prioritize visible text and accessible roles.

## Setup

```bash
cd automation/saleads-mi-negocio
npm install
npm run install:browsers
```

## Run

Use either variable to target any environment:

```bash
SALEADS_LOGIN_URL="https://<your-env>/login" npm run test:saleads-mi-negocio
```

or

```bash
SALEADS_BASE_URL="https://<your-env>/login" npm run test:saleads-mi-negocio
```

## Evidence and report

- Playwright output folder contains:
  - checkpoint screenshots
  - `final_report.json` with PASS/FAIL for:
    - Login
    - Mi Negocio menu
    - Agregar Negocio modal
    - Administrar Negocios view
    - Información General
    - Detalles de la Cuenta
    - Tus Negocios
    - Términos y Condiciones
    - Política de Privacidad
- The report also stores legal page final URLs.
