# SaleADS E2E - Mi Negocio workflow

This directory contains an end-to-end Playwright test that validates the full **Mi Negocio** workflow in SaleADS.ai, including:

- Login with Google
- Sidebar > Mi Negocio navigation
- Agregar Negocio modal validation
- Administrar Negocios account view
- Información General, Detalles de la Cuenta, and Tus Negocios validations
- Términos y Condiciones and Política de Privacidad legal links (same tab or new tab)
- Screenshots at key checkpoints
- Final PASS/FAIL report by step

## Test file

- `saleads_mi_negocio_full_test.spec.js`

## Environment-agnostic execution

No hardcoded domain is used. Provide the login page for the current environment:

```bash
cd /workspace/e2e
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:mi-negocio
```

Accepted URL environment variables:

1. `SALEADS_LOGIN_URL`
2. `SALEADS_URL`
3. `BASE_URL`

If none is provided, the test fails early with a clear message.

## Artifacts

The test writes screenshots and a final JSON report here:

- `e2e/test-artifacts/saleads-mi-negocio-<timestamp>/`

`final-report.json` includes:

- PASS/FAIL per required step
- Error details for failed steps
- Final URLs captured for legal pages
