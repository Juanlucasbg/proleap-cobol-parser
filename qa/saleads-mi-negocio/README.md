# SaleADS Mi Negocio full workflow test

This Playwright suite automates the full `saleads_mi_negocio_full_test` workflow:

1. Login using Google.
2. Open `Negocio -> Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate:
   - `Información General`
   - `Detalles de la Cuenta`
   - `Tus Negocios`
6. Validate legal links:
   - `Términos y Condiciones`
   - `Política de Privacidad`
7. Generate a final PASS/FAIL report per step.

## Configuration

The test is environment-agnostic and does not hardcode any SaleADS domain.

- `SALEADS_BASE_URL` (or `BASE_URL`): Login URL for the target SaleADS environment.
- `HEADLESS` (optional): Set `false` to run headed.

## Run

```bash
cd /workspace/qa/saleads-mi-negocio
npx playwright install chromium
SALEADS_BASE_URL="https://<your-saleads-env>/login" npm run test:saleads-mi-negocio
```

## Evidence output

- Screenshots: `checkpoints/` (created at runtime)
- JSON report: `test-results/saleads_mi_negocio_full_test_report.json`

The JSON report contains PASS/FAIL for each required section plus captured legal URLs.
