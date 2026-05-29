# SaleADS Mi Negocio Full Workflow Test

Playwright E2E test that validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal
4. Administrar Negocios page
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones
9. Politica de Privacidad

The test does **not** hardcode a specific SaleADS domain. Use `SALEADS_START_URL` for the environment under test.

## Setup

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_START_URL="https://<your-environment>/login" npm test
```

For visible browser execution:

```bash
HEADLESS=false SALEADS_START_URL="https://<your-environment>/login" npm run test:headed
```

## Evidence and report

- Screenshots for major checkpoints are stored in the Playwright output folder (`test-results/.../artifacts/`).
- Final report JSON is attached as `saleads-mi-negocio-final-report`.
- The report includes PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad
