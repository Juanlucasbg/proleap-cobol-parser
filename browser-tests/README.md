# SaleADS Mi Negocio Full Test

This Playwright suite automates the full `saleads_mi_negocio_full_test` workflow:

- Login with Google
- Open/validate `Mi Negocio`
- Validate `Agregar Negocio` modal
- Validate `Administrar Negocios` sections
- Validate legal links (`Términos y Condiciones` and `Política de Privacidad`)
- Capture checkpoint screenshots and a final JSON PASS/FAIL report

## Why this works across environments

- No SaleADS domain is hardcoded.
- URL is provided through environment variables.
- Selectors prioritize visible user text (Spanish/English variants where needed).

## Setup

```bash
cd browser-tests
npm install
npx playwright install chromium
```

## Run

Preferred mode (explicit login URL for current environment):

```bash
SALEADS_LOGIN_URL="https://<current-saleads-env>/login" npm run test:saleads
```

Alternative mode (if your harness opens the login page before test starts):

```bash
SALEADS_SKIP_NAVIGATION=true npm run test:saleads
```

## Artifacts

Playwright stores run artifacts under `test-results/`.

Important evidence files are written to the test output evidence folder:

- `01-dashboard-loaded.png`
- `02-mi-negocio-expanded-menu.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios-cuenta.png`
- `05-terminos-y-condiciones.png`
- `06-politica-de-privacidad.png`
- `final-report.json`
