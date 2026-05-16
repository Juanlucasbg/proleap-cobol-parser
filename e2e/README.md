# SaleADS Mi Negocio E2E

This folder contains an end-to-end Playwright test for the full Mi Negocio workflow:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios view validation
- Información General, Detalles de la Cuenta, Tus Negocios checks
- Términos y Condiciones / Política de Privacidad validation (same tab or new tab)
- Final PASS/FAIL report in logs

## Requirements

- Node.js 18+
- Chromium browser for Playwright

## Install

```bash
npm install
npm run pw:install
```

## Run

Use one of these environment variables to provide the login page URL for the current environment:

- `SALEADS_LOGIN_URL`
- `SALEADS_BASE_URL`
- `BASE_URL`

No specific domain is hardcoded in the test.

```bash
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:mi-negocio
```

## Output Evidence

Important screenshots are saved under `test-results/` for:

- dashboard loaded
- Mi Negocio expanded menu
- Crear Nuevo Negocio modal
- Administrar Negocios page
- Términos y Condiciones page
- Política de Privacidad page

The test prints:

- final legal URLs with `[LEGAL_URL]`
- summary report with `[FINAL_REPORT]`
