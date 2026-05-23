# SaleADS Mi Negocio full workflow test

This folder contains a Playwright test that automates the full `saleads_mi_negocio_full_test` workflow:

- Login with Google
- Open `Negocio` -> `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate:
  - `Informacion General`
  - `Detalles de la Cuenta`
  - `Tus Negocios`
  - `Terminos y Condiciones`
  - `Politica de Privacidad`
- Generate a final PASS/FAIL report per requested field

## Key design choices

- Uses visible-text-first selectors and regex fallback locators.
- Does not hardcode any SaleADS domain.
- Handles links that open in the same tab or a new tab.
- Captures screenshots at required checkpoints.
- Writes `final-report.json` into the Playwright test output folder.

## Run

```bash
cd /workspace/qa/saleads-e2e
npm install
npx playwright install --with-deps
export SALEADS_LOGIN_URL="https://<current-saleads-environment>/login"
npm test
```

`SALEADS_LOGIN_URL` is optional if your harness opens the login page before the test starts.

## Output evidence

- HTML report: `playwright-report/`
- Per-test artifacts and screenshots: `test-results/`
- Final JSON report attachment: `final-report.json` within the test output path
