# SaleADS - Mi Negocio Full Workflow E2E

This Playwright suite automates the full flow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Mi Negocio menu checks
3. Agregar Negocio modal checks
4. Administrar Negocios page checks
5. Informacion General checks
6. Detalles de la Cuenta checks
7. Tus Negocios checks
8. Terminos y Condiciones validation (including new tab handling)
9. Politica de Privacidad validation (including new tab handling)
10. PASS/FAIL report generation

## Why this works across environments

- No SaleADS domain is hardcoded.
- The login page URL is provided at runtime via environment variable.
- Element targeting prefers visible text and roles.

## Prerequisites

- Node.js 20+
- Browser dependencies for Playwright

## Install

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd e2e/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm test
```

Optional:

```bash
PW_HEADLESS=false SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:headed
```

## Outputs

- Playwright artifacts in `playwright-report/` and `test-results/`
- JSON final report:
  - `artifacts/saleads_mi_negocio_report.json`
- Screenshots attached to Playwright output and report evidence fields.
