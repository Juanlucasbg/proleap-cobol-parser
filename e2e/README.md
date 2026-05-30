# SaleADS Mi Negocio Full Workflow Test

This folder contains a Playwright E2E automation for the `saleads_mi_negocio_full_test` workflow:

- Login with Google
- Navigate to **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Validate **Administrar Negocios** page sections
- Validate legal links (**Términos y Condiciones** and **Política de Privacidad**)
- Generate a final PASS/FAIL report per required checkpoint

## Why this works across environments

- No domain is hardcoded.
- The login URL is injected at runtime via `SALEADS_LOGIN_URL`.
- UI interaction uses visible text and ARIA roles (button/link/heading/text) instead of brittle CSS selectors.

## Run

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<current-saleads-environment>/login" npm run test:mi-negocio
```

If your environment already opens the browser on the login page (as stated in the workflow), `SALEADS_LOGIN_URL` can be omitted.

## Artifacts

The suite writes:

- Screenshots: `artifacts/screenshots/`
- Playwright raw report: `artifacts/report/playwright-results.json`
- Final workflow summary: `artifacts/report/saleads-mi-negocio-final-report.json`
