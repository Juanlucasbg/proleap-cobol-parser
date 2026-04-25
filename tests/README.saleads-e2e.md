# SaleADS Mi Negocio E2E

This repository now includes an optional Playwright workflow test that validates:

- Login with Google
- Mi Negocio menu expansion
- Agregar Negocio modal content
- Administrar Negocios sections
- Información General / Detalles de la Cuenta / Tus Negocios
- Términos y Condiciones and Política de Privacidad (same-tab or popup)

## Run

```bash
npm install
npm run test:e2e:install
SALEADS_START_URL="https://<current-saleads-environment-login-page>" npm run test:e2e
```

Notes:
- The test is environment-agnostic and only depends on the provided `SALEADS_START_URL`.
- If needed, override the Google account selector with:

```bash
SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com"
```

## Evidence output

- Screenshots and final report JSON:
  - `artifacts/saleads-mi-negocio/`
- Playwright HTML report:
  - `playwright-report/`

The final step-level PASS/FAIL summary is written to:

- `artifacts/saleads-mi-negocio/final-report.json`
