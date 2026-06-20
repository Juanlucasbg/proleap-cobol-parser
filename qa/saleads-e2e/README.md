# SaleADS Mi Negocio E2E

Playwright test suite for the `saleads_mi_negocio_full_test` workflow.

## What this test covers

The scenario validates the complete Mi Negocio flow:

1. Login with Google.
2. Open Mi Negocio menu and validate submenu options.
3. Validate Agregar Negocio modal content.
4. Open Administrar Negocios and validate account sections.
5. Validate Información General.
6. Validate Detalles de la Cuenta.
7. Validate Tus Negocios.
8. Open and validate Términos y Condiciones (new tab or same tab).
9. Open and validate Política de Privacidad (new tab or same tab).
10. Build a PASS/FAIL final report with evidence URLs.

The test captures screenshots at the key checkpoints requested in the workflow.

## Environment portability

No domain is hardcoded. Set one of these variables when running:

- `SALEADS_START_URL` (preferred)
- `SALEADS_URL`
- `BASE_URL`

## Install and run

From `qa/saleads-e2e`:

```bash
npm install
npx playwright install --with-deps chromium
npm run test:saleads-mi-negocio
```

Headed mode (useful for interactive Google auth):

```bash
npm run test:saleads-mi-negocio:headed
```

Example:

```bash
SALEADS_START_URL="https://<your-saleads-environment>/login" npm run test:saleads-mi-negocio:headed
```

## Evidence output

Playwright stores artifacts under `test-results/`, including:

- checkpoint screenshots
- trace/video on failure
- `final-report.json` with PASS/FAIL by section plus legal URLs
