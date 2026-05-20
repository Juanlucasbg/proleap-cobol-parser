# SaleADS Mi Negocio Full Workflow Test

This Playwright test automates the full flow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Return a final PASS/FAIL report for each required section.

## Why this works across environments

- No hardcoded SaleADS domain is used.
- The start URL is read from env vars:
  - `SALEADS_START_URL` (preferred)
  - `SALEADS_LOGIN_URL`
  - `SALEADS_BASE_URL`
- UI interactions are based on visible text/labels and role selectors.

## Setup

```bash
cd /workspace/qa/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
SALEADS_START_URL="https://<current-saleads-environment>/login" npm test
```

For interactive debugging:

```bash
SALEADS_START_URL="https://<current-saleads-environment>/login" npm run test:headed
```

## Artifacts

Playwright stores run artifacts in `test-results` and `playwright-report`.

Checkpoint screenshots captured by the test:

- `01-dashboard.png`
- `02-mi-negocio-menu-expanded.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios-cuenta.png`
- `05-terminos-y-condiciones.png`
- `06-politica-de-privacidad.png`

Final structured report attachment:

- `saleads_mi_negocio_final_report.json`
