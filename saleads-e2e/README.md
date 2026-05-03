# SaleADS E2E Workflows

This folder contains isolated Playwright E2E tests for SaleADS.ai flows.

## Implemented test

- `saleads_mi_negocio_full_test`

Validates:

1. Login with Google and dashboard/sidebar visibility.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal content and optional input/cancel flow.
4. Administrar Negocios account view sections.
5. Informacion General validations.
6. Detalles de la Cuenta validations.
7. Tus Negocios validations.
8. Terminos y Condiciones page validation (same tab or new tab).
9. Politica de Privacidad page validation (same tab or new tab).
10. Final PASS/FAIL report attachment and console summary.

The test does not hardcode any SaleADS domain and can run against any environment.

## Environment

The test expects one of these conditions:

- The browser is already opened on the SaleADS login page, or
- `SALEADS_LOGIN_URL` (or `SALEADS_BASE_URL`) is provided.

## Run

```bash
cd saleads-e2e
npm install
npx playwright install chromium
npm run test:mi-negocio
```

Optional:

```bash
SALEADS_LOGIN_URL="https://your-environment-url/login" npm run test:mi-negocio
```

## Evidence generated

- Screenshots are written under `test-results/screenshots/`.
- A JSON final report is attached to the Playwright test output as:
  - `saleads-mi-negocio-final-report.json`
