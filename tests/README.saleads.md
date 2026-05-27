# SaleADS Mi Negocio workflow test

This repository now includes an end-to-end Playwright test for the full Mi Negocio workflow:

- Login with Google
- Open and validate Mi Negocio menu
- Validate Agregar Negocio modal
- Open Administrar Negocios
- Validate Información General, Detalles de la Cuenta, and Tus Negocios
- Validate Términos y Condiciones and Política de Privacidad (same-tab or new-tab)
- Produce a PASS/FAIL final report per requested field

## Run

```bash
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:saleads-mi-negocio
```

Run headed mode:

```bash
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:saleads-mi-negocio:headed
```

## Environment notes

- The test intentionally does **not** hardcode any SaleADS domain.
- Provide the current environment login URL via `SALEADS_LOGIN_URL` (or `SALEADS_BASE_URL`).
- If Google prompts account selection, the test attempts to select:
  - `juanlucasbarbiergarzon@gmail.com`

## Evidence and report

- Screenshots and JSON report are saved under Playwright `test-results` output.
- Final report file: `saleads-evidence/final-report.json`
