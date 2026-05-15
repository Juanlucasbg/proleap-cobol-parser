# SaleADS E2E tests

This directory contains Playwright tests for SaleADS workflows.

## Test included

- `tests/saleads_mi_negocio_full_test.spec.js`

This test:

1. Logs in with Google.
2. Navigates to `Negocio > Mi Negocio`.
3. Validates the `Agregar Negocio` modal.
4. Opens `Administrar Negocios` and validates all account sections.
5. Validates `Términos y Condiciones` and `Política de Privacidad`, including new-tab behavior.
6. Captures screenshots at important checkpoints.
7. Produces a final PASS/FAIL JSON report with legal page URLs.

## Environment-agnostic execution

Provide the login page URL for the target environment using:

- `SALEADS_LOGIN_URL` (preferred), or
- `SALEADS_URL`.

No domain is hardcoded in the test.

## Run

```bash
cd /workspace/e2e
npm install
npm run install:browsers
SALEADS_LOGIN_URL="https://<current-environment>/login" npm run test:saleads-mi-negocio
```

## Output artifacts

Playwright output is stored under:

- `e2e/artifacts/` (screenshots, traces, videos, report JSON)
- `e2e/playwright-report/` (HTML report)
