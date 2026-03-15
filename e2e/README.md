# SaleADS Mi Negocio E2E test

This folder contains the Playwright test:

- `tests/saleads_mi_negocio_full_test.spec.js`

The test validates the full Mi Negocio workflow, including:

- Google login handoff
- Sidebar navigation (`Negocio` -> `Mi Negocio`)
- `Agregar Negocio` modal checks
- `Administrar Negocios` account view checks
- Legal links (`Términos y Condiciones` and `Política de Privacidad`)
- Evidence screenshots and final PASS/FAIL report

## Setup

From this `e2e` directory:

```bash
npm install
npx playwright install --with-deps chromium
```

## Run

If the browser should start directly from a known environment URL, set `SALEADS_URL`:

```bash
SALEADS_URL="https://your-saleads-environment" npm run test:mi-negocio
```

If you prefer to open the app manually, run in headed mode and navigate to the login page first:

```bash
HEADLESS=false npm run test:mi-negocio
```

## Artifacts

Each execution stores evidence at:

`artifacts/saleads_mi_negocio_full_test/<timestamp>/`

Including:

- checkpoint screenshots (`.png`)
- `final-report.json` with PASS/FAIL per required validation step
