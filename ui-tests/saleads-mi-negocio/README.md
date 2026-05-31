# SaleADS Mi Negocio Full Workflow Test

This Playwright test validates the complete `saleads_mi_negocio_full_test` workflow:

1. Login with Google
2. Expand `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open and validate `Administrar Negocios`
5. Validate `Informacion General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Terminos y Condiciones` (including new-tab handling)
9. Validate `Politica de Privacidad` (including new-tab handling)
10. Generate a final PASS/FAIL report

## Why this is environment-agnostic

- No SaleADS domain is hardcoded.
- The test accepts URL from environment variables:
  - `SALEADS_LOGIN_URL`
  - `SALEADS_BASE_URL`
  - `BASE_URL`

## Run

```bash
cd /workspace/ui-tests/saleads-mi-negocio
npm run install:browsers
SALEADS_LOGIN_URL="https://<your-environment-login-page>" npm test
```

For interactive login debugging:

```bash
HEADED=1 SALEADS_LOGIN_URL="https://<your-environment-login-page>" npm run test:headed
```

## Evidence and report artifacts

- Checkpoint screenshots are captured into the Playwright test output folder (`test-results/.../screenshots`).
- A structured report is written as:
  - `saleads-mi-negocio-final-report.json`
- The report is also printed in terminal logs between:
  - `SALEADS_MI_NEGOCIO_FINAL_REPORT_START`
  - `SALEADS_MI_NEGOCIO_FINAL_REPORT_END`
