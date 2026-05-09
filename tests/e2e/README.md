# SaleADS Mi Negocio E2E

This folder contains the end-to-end test `saleads-mi-negocio-full.spec.js` for:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones / Política de Privacidad legal links (same tab or popup)
- Final PASS/FAIL report per requested section

## Run

```bash
npm run playwright:install
npm run test:saleads
```

## Environment variables

- `SALEADS_LOGIN_URL` (optional): login page URL for the current environment.
  - If omitted, the test uses the already-open page URL.
- `SALEADS_GOOGLE_ACCOUNT` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_UI_SETTLE_MS` (optional): extra UI wait after clicks (default `800`).

## Artifacts

The test captures:

- checkpoint screenshots at key steps
- legal page screenshots
- a `final-report.json` attachment with PASS/FAIL results
