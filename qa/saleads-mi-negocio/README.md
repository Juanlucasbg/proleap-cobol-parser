# SaleADS Mi Negocio Full Workflow Test

This package contains a Playwright E2E test named `saleads_mi_negocio_full_test`.

The test validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios account view sections
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones legal page (same tab or new tab)
9. Política de Privacidad legal page (same tab or new tab)
10. Final PASS/FAIL report per required field

## Environment-agnostic behavior

- No fixed domain is hardcoded.
- If the browser starts on `about:blank`, set `SALEADS_LOGIN_URL` to your environment login page URL.
- If your runner already opens the SaleADS login page before test start, `SALEADS_LOGIN_URL` is not required.

## Variables

- `SALEADS_LOGIN_URL` (optional): login URL for current SaleADS environment.
- `SALEADS_GOOGLE_ACCOUNT` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `HEADLESS` (optional): set `false` to run headed.

## Run

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps
npm test
```

## Artifacts

- Important screenshots are saved under the Playwright test output folder (`checkpoints/` inside the test result directory).
- The final report is saved as:
  - `saleads-mi-negocio-final-report.json`
- Report is also printed in stdout between:
  - `SALEADS_MI_NEGOCIO_FINAL_REPORT_START`
  - `SALEADS_MI_NEGOCIO_FINAL_REPORT_END`
