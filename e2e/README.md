# SaleADS.ai E2E Tests

This folder contains Playwright end-to-end tests for SaleADS.ai workflows.

## Implemented workflow

- `tests/saleads-mi-negocio-full-test.spec.ts`
  - Login with Google
  - Open `Negocio > Mi Negocio`
  - Validate `Agregar Negocio` modal
  - Validate `Administrar Negocios` sections
  - Validate legal links (`Términos y Condiciones`, `Política de Privacidad`)
  - Capture screenshots at key checkpoints
  - Emit a final PASS/FAIL report with evidence URLs

## Setup

```bash
npm install
npx playwright install chromium
```

## Run

Set the environment URL for the current SaleADS environment (dev/staging/prod), then run:

```bash
SALEADS_BASE_URL="https://your-saleads-environment" npm run test:saleads:mi-negocio
```

Optional aliases accepted by the test:

- `SALEADS_LOGIN_URL`
- `BASE_URL`
- `APP_BASE_URL`

## Notes

- The test intentionally avoids hardcoding a specific domain.
- Element selection prioritizes visible text, with Spanish and English label support where applicable.
- If legal pages open in a new tab, the test validates content, captures evidence, closes the tab, and returns to the app tab.
