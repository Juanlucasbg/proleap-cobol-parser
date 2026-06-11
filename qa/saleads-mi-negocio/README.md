# SaleADS Mi Negocio full workflow test

This folder contains a portable Playwright test for the full SaleADS.ai "Mi Negocio" workflow:

- Google login
- Sidebar > Negocio > Mi Negocio expansion
- "Agregar Negocio" modal validation
- "Administrar Negocios" view validation
- "Información General", "Detalles de la Cuenta", and "Tus Negocios" validations
- Legal links validation ("Términos y Condiciones" and "Política de Privacidad"), including new-tab handling
- Screenshot evidence at key checkpoints
- Final PASS/FAIL JSON report

## Why this works in any environment

The test does **not** hardcode any SaleADS URL.  
Set one of these variables before running:

- `SALEADS_START_URL` (preferred)
- `SALEADS_LOGIN_URL`
- `BASE_URL`

It also does not assume a specific domain, only visible UI text.

## Account selector

By default, the test looks for:

- `juanlucasbarbiergarzon@gmail.com`

Override with:

- `SALEADS_GOOGLE_ACCOUNT`

## Run

```bash
cd qa/saleads-mi-negocio
npx playwright install --with-deps chromium
SALEADS_START_URL="https://<your-saleads-env>/login" npm test
```

Headed mode:

```bash
SALEADS_START_URL="https://<your-saleads-env>/login" npm run test:headed
```

## Artifacts

Playwright stores outputs under `test-results/`:

- checkpoint screenshots (`01-...png`, `02-...png`, etc.)
- `final-report.json` with PASS/FAIL per required section and legal final URLs
