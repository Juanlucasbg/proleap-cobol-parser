# SaleADS E2E tests

This folder contains Playwright tests for SaleADS workflows.

## Mi Negocio full workflow test

Test file: `tests/saleads_mi_negocio_full_test.spec.ts`

### What it validates

- Google sign-in entry point (and account selection if shown).
- Left sidebar visibility after login.
- Expansion of `Negocio` > `Mi Negocio`.
- `Agregar Negocio` modal content:
  - `Crear Nuevo Negocio`
  - `Nombre del Negocio`
  - `Tienes 2 de 3 negocios`
  - `Cancelar` and `Crear Negocio`
- `Administrar Negocios` account sections:
  - `Información General`
  - `Detalles de la Cuenta`
  - `Tus Negocios`
  - `Sección Legal`
- Legal links:
  - `Términos y Condiciones`
  - `Política de Privacidad`
  - Supports same-tab navigation or popup/new tab.

### Environment-agnostic behavior

- The test does **not** hardcode any specific SaleADS domain.
- By default it assumes the browser is already on the login page.
- Optionally you can provide an environment URL:
  - `SALEADS_START_URL=<url>` or `BASE_URL=<url>`

### Run

```bash
cd e2e
npm install
npx playwright install chromium
npm run test:mi-negocio
```

Headed mode:

```bash
npm run test:mi-negocio:headed
```

### Artifacts

Artifacts are saved under `e2e/artifacts/`:

- Checkpoint screenshots: `artifacts/checkpoints/`
- Playwright report: `artifacts/playwright-report/`
- JSON execution report:
  - `artifacts/reports/saleads_mi_negocio_full_test.report.json`

The JSON report contains PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
