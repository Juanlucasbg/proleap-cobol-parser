# SaleADS UI E2E tests

This folder contains an end-to-end Playwright test for the full **Mi Negocio** workflow:

- Login with Google (and account selector handling)
- Open **Mi Negocio** menu and validate submenu options
- Validate **Agregar Negocio** modal fields and actions
- Open **Administrar Negocios** and validate all requested sections
- Validate **Información General**, **Detalles de la Cuenta**, and **Tus Negocios**
- Validate legal links (**Términos y Condiciones** and **Política de Privacidad**) including:
  - new-tab or same-tab behavior
  - heading/content checks
  - screenshot and final URL capture
- Emit a final PASS/FAIL report per required field

## Requirements

- Node.js 20+ recommended
- Chromium browser for Playwright:

```bash
npm run install:browsers
```

## Run

From this folder:

```bash
npm test
```

Or run only this spec:

```bash
npm run test:mi-negocio
```

### Environment configuration

This test does not hardcode a SaleADS domain. Use `SALEADS_URL` (or `BASE_URL`) for the current environment login page:

```bash
SALEADS_URL="https://<current-environment-login-url>" npm run test:mi-negocio
```

If your test harness already opens the login page before test execution, you can omit `SALEADS_URL`.

## Evidence and report output

Playwright stores artifacts in `test-results/` and `playwright-report/`.

The spec captures screenshots at key checkpoints and writes `final-report.json` with PASS/FAIL status for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
