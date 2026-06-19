# SaleADS Mi Negocio Full Workflow Test

This Playwright test automates the workflow named `saleads_mi_negocio_full_test`:

- Login with Google
- Open **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Validate **Administrar Negocios** sections
- Validate legal links:
  - **Términos y Condiciones**
  - **Política de Privacidad**
- Capture screenshots at key checkpoints
- Produce a final PASS/FAIL JSON report

## Environment Agnostic Configuration

The test does not hardcode a domain. You must provide the login URL of the current SaleADS environment through an environment variable:

```bash
export SALEADS_LOGIN_URL="https://app.saleads.ai/login"
```

Optional metadata field:

```bash
export SALEADS_ENV="staging"
```

## Run

Install dependencies:

```bash
npm install
npx playwright install chromium
```

Execute the workflow:

```bash
npm run test:saleads-mi-negocio
```

## Evidence Output

- Screenshots: `artifacts/screenshots/`
- Final report: `artifacts/reports/saleads_mi_negocio_full_test.report.json`

The report includes:

- PASS/FAIL for each required validation area
- Captured URLs for legal pages (when validated)
