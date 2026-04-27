# SaleADS Mi Negocio E2E

This folder contains a Playwright end-to-end test for the full **Mi Negocio** workflow.

## Test included

- `tests/saleads-mi-negocio.spec.js`
  - Test name: `saleads_mi_negocio_full_test`
  - Covers:
    1. Login with Google
    2. Mi Negocio menu expansion
    3. Agregar Negocio modal validation
    4. Administrar Negocios page validation
    5. Información General validation
    6. Detalles de la Cuenta validation
    7. Tus Negocios validation
    8. Términos y Condiciones validation (same tab or new tab)
    9. Política de Privacidad validation (same tab or new tab)
    10. Final PASS/FAIL report generation

The test uses visible text selectors whenever possible and captures screenshots at key checkpoints.

## Environment portability

The test does **not** hardcode any SaleADS domain.

- If `SALEADS_BASE_URL` is provided, the test navigates there first.
- If `SALEADS_BASE_URL` is not provided, it assumes the browser is already on the SaleADS login page.

## Run

From repository root:

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
npm run test:mi-negocio
```

Run headed mode:

```bash
npm run test:mi-negocio:headed
```

Set a specific environment URL (optional):

```bash
SALEADS_BASE_URL="https://your-saleads-environment.example" npm run test:mi-negocio
```

## Artifacts

Generated under `e2e/test-results/`:

- `saleads-mi-negocio-report.json`: structured PASS/FAIL report by validation area
- `checkpoints/*.png`: screenshots from important checkpoints
- `artifacts/`: Playwright traces/videos/screenshots on failure
