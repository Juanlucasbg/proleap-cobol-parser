# SaleADS Mi Negocio workflow test

This repository now includes a Playwright end-to-end test for the full SaleADS **Mi Negocio** flow:

- Google login
- Sidebar > Negocio > Mi Negocio
- "Agregar Negocio" modal validation
- "Administrar Negocios" account view validation
- "Información General", "Detalles de la Cuenta", and "Tus Negocios" checks
- Legal links ("Términos y Condiciones" and "Política de Privacidad"), including new-tab handling
- Checkpoint screenshots + JSON final report

## Why this works in any environment

The test does **not** hardcode a SaleADS domain.  
Use an environment-specific URL at runtime:

- `SALEADS_LOGIN_URL` -> Login page URL for dev/staging/prod

## Run

```bash
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<current-environment>/login" npm run test:e2e:mi-negocio:headed
```

## Optional environment variables

- `SALEADS_LOGIN_URL`: login URL for the environment under test.
- `SALEADS_ARTIFACTS_DIR`: where screenshots and `final-report.json` are saved.
- `HEADLESS=false`: run in headed mode when using `npm run test:e2e:mi-negocio`.

## Artifacts

By default, artifacts are written under Playwright output directories.  
Each run includes:

- checkpoint screenshots (`01-dashboard-loaded.png`, etc.)
- `final-report.json` with PASS/FAIL per requested validation area
