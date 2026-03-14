# SaleADS workflow automation

This folder contains a standalone Playwright script for:

- login with Google
- full Mi Negocio module workflow validation
- screenshots at key checkpoints
- tab/navigation handling for legal links
- final PASS/FAIL JSON report per requested section

## Test included

- `saleads_mi_negocio_full_test.js`

## Runtime requirements

- Node.js 18+
- A reachable SaleADS login URL for the target environment (dev/staging/prod)

## Install

```bash
cd automation
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd automation
SALEADS_BASE_URL="https://your-environment-login-url" npm run saleads:mi-negocio
```

Optional:

- `HEADLESS=false` to run with visible browser UI.

Example:

```bash
cd automation
HEADLESS=false SALEADS_BASE_URL="https://your-environment-login-url" npm run saleads:mi-negocio
```

## Evidence and report output

Each run writes to:

- `automation/artifacts/saleads_mi_negocio_full_test_<timestamp>/`

Including:

- checkpoint screenshots (`*.png`)
- final report (`saleads-mi-negocio-full-test_report.json`)

The JSON report includes:

- PASS/FAIL status for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
- captured legal page final URLs
- any validation errors
