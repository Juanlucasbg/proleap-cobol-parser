# SaleADS.ai E2E - Mi Negocio full workflow

This folder contains a Playwright test for the workflow named:

- `saleads_mi_negocio_full_test`

## What it validates

The test automates the full flow requested for SaleADS.ai:

1. Login with Google.
2. Expand `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios` and validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
   - Seccion Legal
5. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
6. Generate a final PASS/FAIL report.

The script captures screenshots at key checkpoints and stores final legal URLs in the report.

## Environment-agnostic usage

No domain is hardcoded. Run against any environment (dev/staging/prod) by providing the login URL:

```bash
cd e2e
npm install
npm run pw:install
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:saleads-mi-negocio
```

Optional:

- `HEADLESS=false` to run headed mode.
- `SALEADS_BASE_URL` can be used instead of `SALEADS_LOGIN_URL`.

## Artifacts

For each execution, artifacts are saved in:

- `e2e/artifacts/<timestamp>/`

Including:

- Checkpoint screenshots
- `final-report.json` with PASS/FAIL per required report field
