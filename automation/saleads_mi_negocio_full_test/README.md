# saleads_mi_negocio_full_test

Environment-agnostic Playwright workflow for validating the SaleADS **Mi Negocio** module.

## What it validates

The script executes and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

It also captures screenshots at major checkpoints and writes a structured JSON report.

## Requirements

- Node.js 18+
- A valid login page URL for the current environment (dev/staging/prod)

## Install

```bash
npm install
```

## Run

```bash
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm test
```

Optional:

- `HEADLESS=false` to run with a visible browser window.
- `SALEADS_TEST_OUTPUT_DIR=/path/to/output` to override artifact location.

## Outputs

By default, the script stores:

- Screenshots in `artifacts/saleads_mi_negocio_full_test/<timestamp>/screenshots/`
- Final report in `artifacts/saleads_mi_negocio_full_test/<timestamp>/report.json`

The report includes:

- PASS/FAIL status for each required validation field
- Captured legal page final URLs
- Evidence paths (screenshots)
