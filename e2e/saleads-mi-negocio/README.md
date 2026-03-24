# SaleADS Mi Negocio Full Workflow E2E

Playwright E2E automation for the full **Mi Negocio** workflow, including:

- Login with Google (without hardcoded environment URL)
- Sidebar navigation validation
- Mi Negocio menu expansion validation
- Agregar Negocio modal validation
- Administrar Negocios page and section validations
- Legal links validation (`Terminos y Condiciones`, `Politica de Privacidad`)
- Screenshot checkpoints
- PASS/FAIL final report in test output

## Requirements

- Node.js 20+ (or 18+ with modern npm)
- Browsers installed for Playwright

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Environment variables

The test is intentionally URL-agnostic. It does not hardcode any SaleADS environment.

- `SALEADS_LOGIN_URL` (optional): full login URL for the current environment.
  - If omitted, the test assumes the browser is already on the login page and opens `about:blank`.
  - In CI/non-interactive runs, this should usually be provided.
- `GOOGLE_ACCOUNT_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.

Examples:

```bash
export SALEADS_LOGIN_URL="https://<your-current-env>/login"
export GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"
```

## Run

Headless:

```bash
npm test
```

Headed:

```bash
npm run test:headed
```

## Evidence and report

- Screenshots are attached to Playwright report and trace/video are enabled on retries.
- The test prints a final PASS/FAIL summary for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad
