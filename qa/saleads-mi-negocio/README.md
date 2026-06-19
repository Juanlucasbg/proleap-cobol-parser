# SaleADS Mi Negocio full workflow automation

This module contains an executable Playwright script for the workflow:

1. Login with Google.
2. Open and validate `Mi Negocio` menu options.
3. Validate the `Agregar Negocio` modal.
4. Open `Administrar Negocios` and validate all required sections.
5. Validate legal links (`Terminos y Condiciones`, `Politica de Privacidad`) including new-tab handling.
6. Produce a PASS/FAIL report for each required checkpoint.

## Environment-agnostic behavior

The script does not hardcode a SaleADS domain.

- Option A (recommended): provide `SALEADS_START_URL` for the current environment login page.
- Option B: provide `CHROME_CDP_URL` to connect to an already-open browser tab that is already on the SaleADS login page.

## Run

```bash
cd qa/saleads-mi-negocio
npm install
npm run run
```

### Optional env vars

- `SALEADS_START_URL`: Login page URL for current environment.
- `CHROME_CDP_URL`: Connect to an existing browser session/tab.
- `HEADLESS=false`: Run headed mode.
- `SLOW_MO_MS=200`: Slow down actions for debugging.

## Output artifacts

For each run, output is saved under:

`qa/saleads-mi-negocio/artifacts/<timestamp>/`

Includes:

- Checkpoint screenshots (`01-...png`, `02-...png`, etc.)
- `final-report.json` with PASS/FAIL status for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad
