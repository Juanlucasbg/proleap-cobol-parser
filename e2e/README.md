# SaleADS Mi Negocio E2E

Playwright suite to validate the full **Mi Negocio** workflow after Google login.

## Goals covered

- Login with Google (continue workflow after login)
- Validate left sidebar and `Mi Negocio` menu
- Validate `Agregar Negocio` modal
- Validate `Administrar Negocios` sections
- Validate legal links (`Términos y Condiciones` and `Política de Privacidad`)
- Capture screenshots at key checkpoints
- Produce final PASS/FAIL report per requested field

## Prerequisites

- Node.js 18+
- Browser session can start on SaleADS login page of any environment, or provide `SALEADS_START_URL`.

## Install

```bash
cd /workspace/e2e
npm install
npx playwright install --with-deps
```

## Run

```bash
cd /workspace/e2e
SALEADS_START_URL="https://<current-env-login-url>" \
SALEADS_GOOGLE_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npm run test:mi-negocio
```

Optional variables:

- `SALEADS_USER_NAME`: expected user name for stricter validation in `Información General`.
- `PW_HEADLESS=false`: run headed.

## Evidence output

Artifacts are generated under Playwright output directories (inside `e2e/test-results`), including:

- Checkpoint screenshots:
  - `01-dashboard-loaded.png`
  - `02-mi-negocio-menu-expanded.png`
  - `03-agregar-negocio-modal.png`
  - `04-administrar-negocios.png`
  - `05-terminos-y-condiciones.png`
  - `06-politica-de-privacidad.png`
- Final reports:
  - `mi-negocio-final-report.json`
  - `mi-negocio-final-report.txt`
