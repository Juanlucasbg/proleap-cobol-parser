# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright test for:

- Google login
- `Mi Negocio` sidebar workflow
- `Agregar Negocio` modal validation
- `Administrar Negocios` account page validation
- Legal links (`Términos y Condiciones` and `Política de Privacidad`)
- Screenshot checkpoints and final PASS/FAIL JSON report

## Prerequisites

- Node.js 18+
- Environment-specific SaleADS login URL

## Install

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" \
SALEADS_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npm test
```

Optional:

- `SALEADS_HEADLESS=false` to run headed mode

## Evidence Output

Playwright stores artifacts under `test-results/`, including:

- Checkpoint screenshots:
  - `01-dashboard-loaded.png`
  - `02-mi-negocio-expanded.png`
  - `03-crear-nuevo-negocio-modal.png`
  - `04-administrar-negocios-page.png`
  - `05-terminos-y-condiciones.png`
  - `06-politica-de-privacidad.png`
- Final report:
  - `saleads-mi-negocio-final-report.json`

The final report contains PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
