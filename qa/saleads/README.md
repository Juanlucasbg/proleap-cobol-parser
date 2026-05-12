# SaleADS E2E Automation

This folder contains a standalone Playwright test for the **Mi Negocio full workflow**.

## Test included

- `tests/saleads_mi_negocio_full_test.spec.js`

It validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal fields
4. Administrar Negocios sections
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones link (same tab or new tab)
9. Politica de Privacidad link (same tab or new tab)
10. Final PASS/FAIL report generation

## Why this works across environments

- No SaleADS domain is hardcoded.
- Runtime URL is injected with environment variables.
- Element selection prioritizes visible text.

## Environment variables

- `SALEADS_LOGIN_URL` (or `SALEADS_URL`): login page URL for the target environment.
- `SALEADS_GOOGLE_EMAIL` (optional): account used in the Google chooser.
  - Default: `juanlucasbarbiergarzon@gmail.com`
- `SALEADS_EXPECTED_NAME_REGEX` (optional): regex source for validating visible user name.
- `SALEADS_EVIDENCE_DIR` (optional): output folder for screenshots and final report.

## Run

```bash
cd qa/saleads
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm run test:saleads:mi-negocio
```

## Evidence and final report

By default, artifacts are saved under:

- `qa/saleads/artifacts/saleads_mi_negocio_full_test/`

Including:

- checkpoint screenshots
- `final_report.json` with PASS/FAIL for each required report field
