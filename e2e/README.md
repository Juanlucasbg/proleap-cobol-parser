# SaleADS E2E workflow

This folder contains an environment-agnostic Playwright test for the complete **Mi Negocio** workflow.

## What it validates

The test file `saleads-mi-negocio.spec.js` covers:

1. Login with Google
2. Expand Mi Negocio menu
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones**
9. Validate **Política de Privacidad**
10. Emit a final PASS/FAIL report for all sections

It also captures screenshots at each important checkpoint and records legal-page final URLs.

## Run

1. Install Playwright browsers (once per machine):

   ```bash
   npx playwright install chromium
   ```

2. Run the test by passing the current environment login URL at runtime:

   ```bash
   SALEADS_LOGIN_URL="https://<your-env-login-url>" npm run test:e2e:mi-negocio
   ```

   You can also use `SALEADS_BASE_URL` or `BASE_URL` instead of `SALEADS_LOGIN_URL`.

## Artifacts

Artifacts are stored in Playwright output directories (`test-results/...`), including:

- Screenshots for each milestone
- `final-report.json` with per-step PASS/FAIL and legal URLs
