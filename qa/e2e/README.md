# SaleADS E2E Tests (Mi Negocio)

This folder contains an environment-agnostic Playwright test for the full
SaleADS Mi Negocio workflow:

- Google login (including account selector handling)
- Sidebar and Mi Negocio menu validations
- Agregar Negocio modal validations
- Administrar Negocios page/sections validations
- Información General, Detalles de la Cuenta, and Tus Negocios checks
- Términos y Condiciones and Política de Privacidad link checks
- Checkpoint screenshots and final JSON report

## 1) Install dependencies

From repository root:

```bash
cd qa/e2e
npm install
npx playwright install --with-deps chromium
```

## 2) Run test

The test assumes you start on the current environment login page.

If you want the test to navigate automatically, provide the current environment
URL via `SALEADS_LOGIN_URL` (or `SALEADS_URL`).

```bash
cd qa/e2e
SALEADS_LOGIN_URL="https://your-current-saleads-environment" npm run test:saleads-mi-negocio
```

Headed mode:

```bash
cd qa/e2e
SALEADS_LOGIN_URL="https://your-current-saleads-environment" npm run test:saleads-mi-negocio:headed
```

## 3) Evidence and report

Artifacts are generated under Playwright's `test-results` output directory:

- Checkpoint screenshots (dashboard, expanded menu, modal, account view, legal pages)
- `final-report.json` with PASS/FAIL by required field
- Captured legal final URLs for:
  - Términos y Condiciones
  - Política de Privacidad

## Notes

- No fixed domain is hardcoded.
- Selectors prefer visible text and role-based queries.
- UI load waits are applied after each relevant click/navigation.
- New-tab legal links are handled, validated, and closed before returning to app.
