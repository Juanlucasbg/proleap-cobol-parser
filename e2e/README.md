# SaleADS.ai E2E tests

This folder contains browser automation for SaleADS.ai workflows.

## Included scenario

- `saleads_mi_negocio_full_test`
  - Login with Google.
  - Validate `Mi Negocio` menu and submenu options.
  - Validate `Agregar Negocio` modal fields and actions.
  - Validate `Administrar Negocios` sections.
  - Validate legal links (`Términos y Condiciones`, `Política de Privacidad`) including new-tab handling.
  - Capture screenshots at key checkpoints.
  - Emit a final PASS/FAIL report for all requested validation groups.

## Setup

```bash
cd /workspace/e2e
npm install
npm run install:browsers
```

## Run

If the test runner starts on `about:blank`, provide a login page URL through an environment variable:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm run test:saleads-mi-negocio
```

Optional headed mode:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm run test:saleads-mi-negocio:headed
```

## Evidence

Checkpoint screenshots and the final JSON report are written to Playwright's test output directories for each run.
