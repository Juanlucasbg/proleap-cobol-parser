# SaleADS Mi Negocio E2E

This folder contains an end-to-end Playwright test for the full "Mi Negocio" workflow:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Informacion General, Detalles de la Cuenta, and Tus Negocios checks
- Terminos y Condiciones and Politica de Privacidad checks (same tab or new tab)
- Checkpoint screenshots and a final PASS/FAIL JSON report

## Setup

```bash
cd e2e
npm install
npx playwright install chromium
```

## Run

```bash
cd e2e
npm test
```

Optional environment variables:

- `SALEADS_LOGIN_URL`: login URL for the current SaleADS environment.
  - No domain is hardcoded in the test.
- `HEADED=1`: run browser in headed mode.

## Output artifacts

The test stores evidence under:

`e2e/e2e-artifacts/saleads-mi-negocio/<timestamp>/`

Including:

- Checkpoint screenshots (`.png`)
- `final-report.json` with PASS/FAIL per required section plus captured legal URLs
