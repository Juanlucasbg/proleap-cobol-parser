# SaleADS Mi Negocio Full Workflow Test

This repository now includes a Playwright end-to-end test named:

- `saleads_mi_negocio_full_test`

File path:

- `tests/saleads-mi-negocio-full.spec.js`

## What it validates

The test automates the full flow requested for SaleADS:

1. Login with Google.
2. Expand and validate the **Mi Negocio** menu.
3. Open and validate the **Agregar Negocio** modal.
4. Open and validate **Administrar Negocios** sections.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (including new-tab behavior).
9. Validate **Política de Privacidad** (including new-tab behavior).
10. Produce a final PASS/FAIL report for every required field.

## Environment-agnostic usage

No domain is hardcoded. Run either with a runtime URL for the current environment:

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:saleads-mi-negocio
```

Or pre-open the SaleADS login page and run without `SALEADS_LOGIN_URL`.

Optional:

- `SALEADS_EXPECTED_USER_NAME` for strict user-name validation.
- `HEADLESS=false` to run headed mode.

## Evidence generated

At key checkpoints the test captures screenshots and stores report data:

- Playwright artifacts: `test-results/` (screenshots, traces, videos).
- Final structured report:
  - `test-results/saleads-mi-negocio-report.json`

