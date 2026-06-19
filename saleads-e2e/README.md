# SaleADS Mi Negocio Full Workflow E2E Test

This suite implements an environment-agnostic Playwright test for the complete SaleADS.ai **Mi Negocio** workflow:

- Google login (including optional account chooser)
- Sidebar and **Mi Negocio** menu expansion
- **Agregar Negocio** modal validations
- **Administrar Negocios** page validations
- **Información General**, **Detalles de la Cuenta**, and **Tus Negocios** checks
- **Términos y Condiciones** and **Política de Privacidad** validations, including new-tab handling
- Required screenshots and final PASS/FAIL report output

## Why this is environment-agnostic

- No hardcoded domain or environment URL is used in the test logic.
- You can run against any environment by setting `SALEADS_START_URL`.
- If your runner already starts on the login page, `SALEADS_START_URL` can be omitted.

## Run

```bash
cd saleads-e2e
npm install
npx playwright install --with-deps
SALEADS_START_URL="https://<your-saleads-env>/login" npm test
```

## Artifacts

Playwright stores screenshots, trace, and video in test output directories.  
The test also logs a machine-readable block:

- `FINAL_REPORT_START`
- JSON report for all required fields
- `FINAL_REPORT_END`

and attaches `final-report.json` to the run output.
