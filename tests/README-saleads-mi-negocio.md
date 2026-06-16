# SaleADS Mi Negocio full workflow test

This repository now includes an end-to-end Playwright test for the `saleads_mi_negocio_full_test` workflow.

## What it validates

The test covers:

1. Login with Google.
2. Mi Negocio menu expansion and submenu visibility.
3. Agregar Negocio modal content validation.
4. Administrar Negocios account page sections.
5. Información General validation.
6. Detalles de la Cuenta validation.
7. Tus Negocios validation.
8. Términos y Condiciones navigation (same tab or new tab).
9. Política de Privacidad navigation (same tab or new tab).
10. Final JSON report with PASS/FAIL by required field.

The test always uses visible text selectors first and captures screenshots at key checkpoints.

## Environment-agnostic behavior

No SaleADS domain is hardcoded. Provide the login page URL for the current environment:

- `SALEADS_LOGIN_URL`
- or `SALEADS_BASE_URL`
- or `BASE_URL`

## Run

```bash
npm install
npx playwright install chromium
npm run test:saleads-mi-negocio
```

For headed mode:

```bash
npm run test:saleads-mi-negocio:headed
```

## Outputs

- Playwright HTML report: `playwright-report/`
- Test artifacts and screenshots: `test-results/`
- Final structured step report (JSON): generated as `final-report.json` in the test output folder.
