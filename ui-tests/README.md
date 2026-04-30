# SaleADS Mi Negocio E2E Tests

This folder contains a Playwright test for the full Mi Negocio workflow:

- Login with Google
- Open `Negocio > Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate:
  - `Información General`
  - `Detalles de la Cuenta`
  - `Tus Negocios`
- Validate legal links:
  - `Términos y Condiciones`
  - `Política de Privacidad`
- Produce a final PASS/FAIL JSON report per requested step
- Capture screenshots at important checkpoints

## Environment-agnostic behavior

The test is designed to avoid dependency on a single domain:

- It can start on the already-open login page (default behavior).
- Optionally set one of these environment variables to navigate explicitly:
  - `SALEADS_LOGIN_URL`
  - `SALEADS_URL`
  - `BASE_URL`
- It uses visible text and role-based selectors as primary strategy.

## Setup

From repository root:

```bash
cd ui-tests
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd ui-tests
npm run test:e2e
```

Run in headed mode:

```bash
cd ui-tests
PW_HEADED=1 npm run test:e2e
```

Optional Google account selector override:

```bash
SALEADS_GOOGLE_ACCOUNT_EMAIL="your-account@gmail.com" npm run test:e2e
```

## Output

- Screenshots and artifacts: `ui-tests/test-results/`
- HTML report: `ui-tests/playwright-report/`
- Attached JSON artifact in test output: `mi-negocio-final-report`
