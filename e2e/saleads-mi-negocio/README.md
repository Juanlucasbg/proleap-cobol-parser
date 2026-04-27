# SaleADS Mi Negocio E2E Workflow Test

This folder contains the Playwright implementation for:

- `saleads_mi_negocio_full_test`

The test validates the complete login + Mi Negocio workflow and writes evidence artifacts:

- Checkpoint screenshots in `artifacts/screenshots/`
- Final structured report in `artifacts/final-report.json`

## Prerequisites

- Node.js 20+ (Node 22 is recommended)
- Access to a browser session that can authenticate against the target SaleADS environment

## Install

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

If `SALEADS_URL` is set, the test navigates to that page.  
If not set, the test assumes the browser starts already on the SaleADS login page.

```bash
cd /workspace/e2e/saleads-mi-negocio
SALEADS_URL="https://<current-environment-login-page>" npm test
```

Headed mode:

```bash
npm run test:headed
```

## Environment-agnostic behavior

- No hardcoded SaleADS domain is used.
- Selectors prioritize visible text and accessibility roles.
- Spanish text matching supports accent and non-accent variants.
- Legal links support same-tab navigation or popup/new-tab flows.

## Final report format

`artifacts/final-report.json` contains:

- `results` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
- `evidence.termsUrl` and `evidence.privacyUrl`
- screenshot paths
