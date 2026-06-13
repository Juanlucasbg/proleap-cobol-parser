# SaleADS E2E Tests

This folder contains a Playwright end-to-end test for the **SaleADS.ai Mi Negocio** workflow.

## Workflow covered

- Login with Google (including optional account selection)
- Open `Negocio` -> `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate:
  - `Información General`
  - `Detalles de la Cuenta`
  - `Tus Negocios`
  - `Términos y Condiciones`
  - `Política de Privacidad`
- Build final PASS/FAIL report as JSON

The test captures screenshots at key checkpoints and records final legal URLs.

## Environment support

No domain is hardcoded. Configure environment with:

- `SALEADS_LOGIN_URL` (preferred), or
- `SALEADS_BASE_URL`

Example:

```bash
cd e2e
npm install
npx playwright install --with-deps
SALEADS_LOGIN_URL="https://your-saleads-login-url" npm run test:saleads-mi-negocio:headed
```

> Google login flows often require headed mode. Use `--headed` when needed.

## Artifacts

Playwright outputs include:

- checkpoint screenshots
- html report
- `saleads-mi-negocio-final-report.json` with PASS/FAIL per required validation step
