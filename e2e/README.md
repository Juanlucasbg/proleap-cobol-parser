# SaleADS Mi Negocio E2E

This folder contains the `saleads_mi_negocio_full_test` Playwright test that validates:

- Google login flow (including account selector handling)
- `Negocio` -> `Mi Negocio` navigation
- `Agregar Negocio` modal content
- `Administrar Negocios` sections
- `Información General`, `Detalles de la Cuenta`, `Tus Negocios`
- Legal links for `Términos y Condiciones` and `Política de Privacidad`
- Final PASS/FAIL report generation per validation area

## Environment-agnostic behavior

The test does **not** hardcode a SaleADS domain.

- If browser is already on the SaleADS login page, it uses the current page.
- If browser starts on `about:blank`, set:
  - `SALEADS_LOGIN_URL`, or
  - `SALEADS_URL`

Example:

```bash
SALEADS_LOGIN_URL="https://your-saleads-env/login" npm run test:mi-negocio
```

## Install and run

From this `e2e` directory:

```bash
npm install
npm run install:browsers
npm run test:mi-negocio
```

Headed mode:

```bash
npm run test:mi-negocio:headed
```

## Artifacts

- Screenshots: `artifacts/screenshots/`
- Final workflow report: `artifacts/reports/mi-negocio-final-report.json`
- Playwright JSON report: `artifacts/reports/playwright-results.json`
- Playwright HTML report: `artifacts/reports/html-report/`

## Notes

- Selectors prefer visible text and role-based queries.
- After every click, the test waits for UI load states.
- Legal links support same-tab navigation or popup/new-tab behavior.
