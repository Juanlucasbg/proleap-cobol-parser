# SaleADS Mi Negocio Full Workflow Test

Playwright E2E test that validates the full `Mi Negocio` workflow after Google login, including legal links and evidence capture.

## Requirements

- Node.js 20+
- Accessible SaleADS environment URL (dev/staging/prod)
- Playwright Chromium browser

## Install

```bash
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_URL="https://<current-environment-login-url>" npm test
```

Optional overrides:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADED=1` for headed mode

## Artifacts

Execution artifacts are generated under `test-results/`:

- Checkpoint screenshots for dashboard/menu/modal/account/legal pages
- `saleads-mi-negocio-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
- Final URLs captured for legal pages
