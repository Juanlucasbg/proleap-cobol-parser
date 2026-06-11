# SaleADS Mi Negocio full workflow test

This Playwright spec validates the full workflow requested in `saleads_mi_negocio_full_test`:

- Google login (including account chooser handling)
- `Negocio` -> `Mi Negocio` menu expansion
- `Agregar Negocio` modal validation
- `Administrar Negocios` page validation
- `Información General`, `Detalles de la Cuenta`, and `Tus Negocios` checks
- Legal link validation for `Términos y Condiciones` and `Política de Privacidad`
- Screenshot capture at key checkpoints
- Final PASS/FAIL report artifact

## Run

```bash
npm install
npx playwright install --with-deps chromium
SALEADS_BASE_URL="https://<current-saleads-environment>" npm run test:saleads
```

## Environment-agnostic behavior

- The test does **not** hardcode a SaleADS domain.
- It uses `SALEADS_BASE_URL` (or `SALEADS_URL` / `BASE_URL`) when provided.
- If your runner already opens the SaleADS login page, the test can continue from that page.

## Evidence outputs

- Screenshots and JSON report are saved to:
  - `saleads-artifacts/`
- Playwright output is available in:
  - `playwright-report/`
  - `test-results/`
