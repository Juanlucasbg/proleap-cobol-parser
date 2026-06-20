# SaleADS Mi Negocio full workflow test

This folder contains a Playwright-based script for the workflow:

- Login with Google
- Navigate to **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Validate **Administrar Negocios** sections
- Validate legal links (**Terminos y Condiciones** and **Politica de Privacidad**)
- Generate a final PASS/FAIL report and screenshots

## Script

- `saleads_mi_negocio_full_test.js`

## Prerequisites

Install Playwright in your environment (project-level or global):

```bash
npm install playwright
```

## Run modes

### 1) URL-based mode (recommended for CI)

No domain is hardcoded. Provide the login URL of the current SaleADS environment:

```bash
SALEADS_LOGIN_URL="https://your-current-saleads-env/login" \
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
node automation/saleads_mi_negocio_full_test.js
```

### 2) Existing browser mode (when browser is already on login page)

Connect to a running Chromium instance through CDP:

```bash
PLAYWRIGHT_CDP_URL="http://127.0.0.1:9222" \
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
node automation/saleads_mi_negocio_full_test.js
```

## Optional env vars

- `HEADLESS=true|false` (default: `false`)
- `SLOW_MO=<milliseconds>` (default: `0`)

## Output

- Final PASS/FAIL JSON report in stdout with these keys:
  - `Login`
  - `Mi Negocio menu`
  - `Agregar Negocio modal`
  - `Administrar Negocios view`
  - `Información General`
  - `Detalles de la Cuenta`
  - `Tus Negocios`
  - `Términos y Condiciones`
  - `Política de Privacidad`
- Evidence JSON with:
  - Screenshot file paths
  - Final URLs for legal pages
- Screenshots saved under:
  - `artifacts/saleads_mi_negocio_full_test/<timestamp>/`
