# SaleADS.ai E2E - Mi Negocio Full Workflow

This folder contains a Playwright test that validates the complete **Mi Negocio** workflow described in the automation prompt:

- Google login (and continue after login)
- Sidebar / Mi Negocio menu validations
- Agregar Negocio modal validations
- Administrar Negocios page validations
- Información General / Detalles de la Cuenta / Tus Negocios validations
- Términos y Condiciones / Política de Privacidad validation (same tab or new tab)
- Checkpoint screenshots and final PASS/FAIL report

## Test file

- `tests/saleads-mi-negocio-full.spec.ts`

## Install

```bash
cd /workspace/e2e
npm install
npx playwright install --with-deps chromium
```

## Run

If your browser session already starts on the SaleADS login page, just run:

```bash
npm run test:saleads-mi-negocio
```

If the test starts on a blank page, pass a login URL from the environment (no hardcoded domain in the test):

```bash
SALEADS_LOGIN_URL="https://<your-current-env>/login" npm run test:saleads-mi-negocio
```

Run headed mode for troubleshooting:

```bash
SALEADS_LOGIN_URL="https://<your-current-env>/login" npm run test:saleads-mi-negocio:headed
```

## Output evidence

Playwright stores artifacts under `test-results/` and `playwright-report/`, including:

- Dashboard screenshot
- Mi Negocio expanded menu screenshot
- Agregar Negocio modal screenshot
- Administrar Negocios full-page screenshot
- Términos y Condiciones screenshot
- Política de Privacidad screenshot
- JSON report: `saleads-mi-negocio-report.json` with:
  - PASS/FAIL for each required validation field
  - captured legal URLs
  - failure messages (if any)
