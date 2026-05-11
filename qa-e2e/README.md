# SaleADS Mi Negocio full workflow test

This folder contains an end-to-end Playwright test for the workflow:

- Login with Google
- Open `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate account sections
- Validate legal links (`Términos y Condiciones` and `Política de Privacidad`)
- Produce a final PASS/FAIL report per required section

## Run

```bash
cd qa-e2e
npm install
npx playwright install chromium
SALEADS_START_URL="https://<current-environment-login-url>" npm test
```

### Environment variables

- `SALEADS_START_URL` (preferred): login URL for current environment.
- `SALEADS_URL` or `BASE_URL`: accepted fallback names.
- `HEADLESS=false`: run headed.

## Evidence

The test captures screenshots at key checkpoints and writes a JSON report:

- Screenshots: Playwright test output folder (`checkpoints/`)
- Report: `saleads_mi_negocio_full_test_report.json`
