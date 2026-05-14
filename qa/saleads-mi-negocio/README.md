# SaleADS Mi Negocio - Full Workflow Test

Playwright E2E test that covers the full `saleads_mi_negocio_full_test` flow:

1. Login with Google.
2. Open `Negocio > Mi Negocio`.
3. Validate the `Agregar Negocio` modal.
4. Open and validate `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (same-tab or new-tab navigation).
9. Validate `Política de Privacidad` (same-tab or new-tab navigation).
10. Generate a final PASS/FAIL JSON report.

## Why this is environment-agnostic

- No hardcoded SaleADS domain is used.
- You can point the test to any environment via `SALEADS_LOGIN_URL`.
- Selectors prioritize visible UI text (`Negocio`, `Mi Negocio`, `Agregar Negocio`, etc.).

## Run

```bash
cd /workspace/qa/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<current-saleads-env>/login" npm test
```

Optional runtime variables:

- `SALEADS_LOGIN_URL`: Login URL for the active environment.
- `SALEADS_ARTIFACTS_DIR`: Output folder for screenshots/report (default: `artifacts/saleads-mi-negocio`).

## Evidence generated

- Checkpoint screenshots in `artifacts/saleads-mi-negocio/screenshots`.
- Final report in `artifacts/saleads-mi-negocio/final-report.json`.
