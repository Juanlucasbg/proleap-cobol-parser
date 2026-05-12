# SaleADS E2E Automation

This folder contains cross-environment browser automation for SaleADS.ai.

## Test implemented

- `saleads_mi_negocio_full_test.js`
  - Logs in with Google
  - Validates Mi Negocio menu workflow
  - Validates account sections
  - Validates legal links (including new-tab flows)
  - Captures screenshots at key checkpoints
  - Generates a final PASS/FAIL report per required section

## Environment-agnostic execution

No hardcoded environment URL is used.

Use one of these modes:

1. Connect to an already-open browser/page (recommended when login page is already loaded):

```bash
PLAYWRIGHT_WS_ENDPOINT=<ws-endpoint> npm run test:mi-negocio
```

2. Launch a browser and navigate using an environment-provided login URL:

```bash
SALEADS_LOGIN_URL=<current-environment-login-url> npm run test:mi-negocio
```

Optional:

- `HEADLESS=false` to run with visible browser UI.

## Output artifacts

The test writes artifacts under:

`e2e/artifacts/saleads_mi_negocio_full_test-<timestamp>/`

Including:

- checkpoint screenshots
- `final-report.json` with PASS/FAIL entries for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
