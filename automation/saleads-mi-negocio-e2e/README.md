# SaleADS Mi Negocio Full Workflow Test

This directory contains the Playwright test `saleads_mi_negocio_full_test` that validates:

1. Login with Google.
2. Sidebar navigation and **Mi Negocio** menu expansion.
3. **Agregar Negocio** modal content.
4. **Administrar Negocios** page sections.
5. Legal links for **Términos y Condiciones** and **Política de Privacidad**.
6. Final PASS/FAIL report output.

## Why this works in any environment

- The test does not hardcode any SaleADS domain.
- Use one of these environment variables:
  - `SALEADS_LOGIN_URL` (preferred, full login URL of current environment)
  - `SALEADS_BASE_URL` (or `BASE_URL`)
- Selectors are text-driven and based on visible labels.

## Install

```bash
cd automation/saleads-mi-negocio-e2e
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" npm test
```

Headed mode:

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:headed
```

## Output evidence

- Checkpoint screenshots are attached to Playwright test artifacts.
- Failure screenshots/videos/traces are retained.
- Final structured report:
  - `test-results/saleads_mi_negocio_full_test-report.json`
- The report includes PASS/FAIL by requested field and legal final URLs.
