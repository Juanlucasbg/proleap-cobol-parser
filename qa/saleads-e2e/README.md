# SaleADS Mi Negocio Full Workflow Test

This folder contains a standalone Playwright E2E test named:

- `saleads_mi_negocio_full_test`

It automates the requested workflow:

1. Login with Google
2. Open `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Informacion General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Terminos y Condiciones` (supports new tab or same tab)
9. Validate `Politica de Privacidad` (supports new tab or same tab)
10. Emit final PASS/FAIL report per section

## Runtime requirements

- Node.js 20+
- Chromium dependencies installed by Playwright when needed

## Install

```bash
cd /workspace/qa/saleads-e2e
npm install
npx playwright install chromium
```

## Run

```bash
cd /workspace/qa/saleads-e2e
SALEADS_LOGIN_URL="https://<current-env-login-page>" npm run test:mi-negocio
```

Optional environment variables:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS` (`true` by default; set `HEADLESS=false` for headed mode)
- `SALEADS_ACTION_TIMEOUT_MS` (default `20000`)
- `SALEADS_WAIT_AFTER_CLICK_MS` (default `1200`)

## Evidence output

On each run, screenshots and report are generated under:

`qa/saleads-e2e/artifacts/saleads_mi_negocio_full_test/<timestamp>/`

The JSON report includes:

- PASS/FAIL for each requested report field
- captured final URL for legal documents
- screenshot paths
