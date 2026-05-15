# SaleADS Mi Negocio Full Workflow Test

This Playwright suite implements the `saleads_mi_negocio_full_test` workflow:

- Login with Google
- Navigate to `Negocio` -> `Mi Negocio`
- Validate `Agregar Negocio` modal
- Validate `Administrar Negocios` sections
- Validate legal links (`Términos y Condiciones`, `Política de Privacidad`)
- Capture screenshots at key checkpoints
- Produce a final PASS/FAIL report for each required section

## Environment-agnostic behavior

No domain is hardcoded. The test reads the login URL from:

- `SALEADS_LOGIN_URL`, or
- `BASE_URL`

If neither variable is provided, the test expects to already be on the SaleADS login page (non-blank page).

## Setup

```bash
cd automation/saleads-mi-negocio
npm install
npm run pw:install
```

## Run

Headless:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-host>/login" npm run test:mi-negocio
```

Headed:

```bash
HEADLESS=false SALEADS_LOGIN_URL="https://<your-saleads-host>/login" npm run test:mi-negocio:headed
```

## Outputs

- Checkpoint screenshots: `artifacts/screenshots/`
- Playwright HTML report: `playwright-report/`
- Final validation matrix: attached as `saleads-mi-negocio-final-report` in test artifacts
