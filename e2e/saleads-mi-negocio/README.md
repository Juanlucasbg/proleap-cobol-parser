# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic Playwright test for:

- Google login on SaleADS.ai
- Full `Mi Negocio` workflow validation
- Legal links validation (`Términos y Condiciones`, `Política de Privacidad`)
- Screenshot evidence at key checkpoints
- Final PASS/FAIL report per requested validation block

## Test file

- `tests/saleads-mi-negocio-full.spec.ts`

## Environment configuration

Do **not** hardcode domains in test code. Use one of these variables:

- `SALEADS_LOGIN_URL` (recommended)
- `PLAYWRIGHT_TEST_BASE_URL` (fallback)

If neither variable is set, the test expects a non-blank page context and will fail with an explicit error.

## Install browsers

```bash
cd /workspace/e2e/saleads-mi-negocio
npx playwright install --with-deps chromium
```

## Run

Headless:

```bash
cd /workspace/e2e/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm test
```

Headed:

```bash
cd /workspace/e2e/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm run test:headed
```

## Outputs

By default, outputs are written to:

- `test-results/saleads_mi_negocio_full_test/`

Includes:

- Screenshots:
  - `01-dashboard-loaded.png`
  - `02-mi-negocio-menu-expanded.png`
  - `03-agregar-negocio-modal.png`
  - `04-administrar-negocios-page.png`
  - `05-terminos-y-condiciones.png`
  - `06-politica-de-privacidad.png`
- JSON report:
  - `saleads_mi_negocio_full_test-report.json`

You can override output location with:

- `SALEADS_OUTPUT_DIR`
