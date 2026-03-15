# SaleADS E2E: Mi Negocio Full Workflow

This module contains an environment-agnostic Playwright test named:

- `saleads_mi_negocio_full_test`

It validates the complete workflow requested for:

1. Google login
2. Mi Negocio menu expansion
3. Agregar Negocio modal validations
4. Administrar Negocios page validations
5. Información General validations
6. Detalles de la Cuenta validations
7. Tus Negocios validations
8. Términos y Condiciones link validations
9. Política de Privacidad link validations
10. Final PASS/FAIL report output

## Important behavior

- No specific domain is hardcoded.
- The test relies on visible text selectors whenever possible.
- It handles both same-tab and new-tab legal page navigation.
- It captures screenshots at critical checkpoints.
- It outputs a final JSON report with PASS/FAIL per required field.

## Run

From repository root:

```bash
cd saleads-e2e
npm run pw:install
npm run test:saleads
```

Optional:

- Run headed mode:

```bash
HEADLESS=false npm run test:saleads
```

## Evidence generated

Playwright stores screenshots and attachments in its test output folder (`test-results/`) and HTML report folder (`playwright-report/`).  
The test also emits a `final-report.json` attachment with step-by-step status and captured legal URLs.
