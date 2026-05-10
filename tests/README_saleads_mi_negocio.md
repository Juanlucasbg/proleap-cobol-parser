# SaleADS Mi Negocio Full Workflow Test

This repository includes an end-to-end Playwright test for the complete **Mi Negocio** flow:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Información General / Detalles de la Cuenta / Tus Negocios validation
- Términos y Condiciones validation
- Política de Privacidad validation
- Final PASS/FAIL report generation

## Environment-agnostic execution

No SaleADS domain is hardcoded in the test.

Set one of these environment variables when running:

- `SALEADS_LOGIN_URL` (preferred)
- `SALEADS_URL`
- `SALEADS_BASE_URL`

Example:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm run test:saleads-mi-negocio
```

## Install and run

```bash
npm install
npx playwright install chromium
npm run test:saleads-mi-negocio
```

## Evidence artifacts

Playwright stores execution artifacts in `test-results/`, including:

- Checkpoint screenshots
- Failure screenshots/video/trace
- `saleads_mi_negocio_final_report.json` with PASS/FAIL by required field and legal URLs
