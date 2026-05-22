# SaleADS Mi Negocio workflow test

This folder contains an environment-agnostic Playwright test for the complete SaleADS **Mi Negocio** workflow requested by automation job `saleads_mi_negocio_full_test`.

## What it validates

The test performs and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

It also:

- waits for UI load after each click,
- validates popup/new-tab legal links and returns to the app,
- captures checkpoint screenshots,
- writes a final JSON report and legal URLs to an evidence folder.

## Setup

```bash
cd /workspace/qa
npm install
npm run install:browsers
```

## Run

Set `SALEADS_START_URL` to the login page URL of whichever environment you want to test (dev/staging/prod), then run:

```bash
cd /workspace/qa
SALEADS_START_URL="https://<current-env-login-page>" npm run test:saleads-mi-negocio
```

Optional:

- `HEADLESS=false` to run headed.
- `SALEADS_EVIDENCE_DIR=/custom/path` is not required; evidence is saved under `qa/evidence/`.

## Evidence outputs

Each run creates a timestamped folder:

`qa/evidence/saleads-mi-negocio-<timestamp>/`

Artifacts include:

- checkpoint screenshots (`*.png`)
- `legal-urls.txt`
- `final-report.json`
