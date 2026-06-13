# SaleADS Mi Negocio Full Workflow Test

This Playwright test automates the full workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones`
9. Validate `Política de Privacidad`
10. Emit final PASS/FAIL report

## Why it works in any environment

- No hardcoded domain is used.
- The login page is read from environment variables:
  - `SALEADS_LOGIN_URL` (preferred)
  - or `SALEADS_BASE_URL`
- Selectors are mostly based on visible text instead of environment-specific CSS.

## Run

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<current-env>/login" npm test
```

Headed mode (recommended for Google login handling):

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" HEADLESS=false npm run test:headed
```

## Output artifacts

- Screenshots for key checkpoints are captured in Playwright test output.
- A final machine-readable report is generated as `final-report.json` and attached to the Playwright run.
- Legal page final URLs are captured in that report.
