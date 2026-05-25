# SaleADS Mi Negocio Full Workflow Test

This directory contains the `saleads_mi_negocio_full_test` Playwright suite.

## What it validates

The test automates and validates the full flow described in the automation brief:

1. Login with Google (and account selection when shown)
2. Open `Negocio` -> `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Informacion General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Terminos y Condiciones` (including new-tab handling)
9. Validate `Politica de Privacidad` (including new-tab handling)
10. Produce step-by-step PASS/FAIL report

Screenshots are captured at key checkpoints and attached to the Playwright output.

## Environment-agnostic setup

The suite does not hardcode any domain. Provide the target environment URL via:

- `SALEADS_URL`, or
- `BASE_URL`

Example:

```bash
export SALEADS_URL="https://your-saleads-environment.example.com"
```

## Run

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
npm test
```

For headed mode:

```bash
npm run test:headed
```

## Evidence output

After execution, review:

- `test-results/` for screenshots and the JSON final report:
  - `saleads-mi-negocio-final-report.json`
- `playwright-report/` for the HTML report
