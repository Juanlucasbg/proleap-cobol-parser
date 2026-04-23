# SaleADS Mi Negocio E2E

Playwright suite that implements the full `saleads_mi_negocio_full_test` workflow:

- Login with Google (or continue if session is already authenticated)
- Validate Mi Negocio menu expansion
- Validate "Agregar Negocio" modal
- Validate "Administrar Negocios" account sections
- Validate legal links for "Términos y Condiciones" and "Política de Privacidad"
- Capture screenshots at key checkpoints
- Emit a structured final PASS/FAIL report per step

## Why this is environment-agnostic

- No domain is hardcoded.
- The entry URL is provided at runtime with `SALEADS_BASE_URL`.
- Selectors prioritize visible text in Spanish labels from the workflow.

## Run

```bash
cd saleads-e2e
npm install
npx playwright install chromium
SALEADS_BASE_URL="https://<current-environment-login-url>" npm test
```

Optional:

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)

## Artifacts

- JSON final report:
  - `saleads-e2e/artifacts/saleads_mi_negocio_full_test_report.json`
- Checkpoint screenshots:
  - `saleads-e2e/artifacts/screenshots/*.png`
- Playwright failure assets:
  - `saleads-e2e/test-results/`
  - `saleads-e2e/playwright-report/`

## Notes

- If `SALEADS_BASE_URL` is not set and the browser starts on `about:blank`, the test fails fast and still writes the final JSON report with all steps as `FAIL`.
- If legal content opens in a new tab, the test validates it, captures evidence, records the URL, and returns to the app.
