# SaleADS Mi Negocio full workflow test

This folder contains a Playwright end-to-end test for the full **Mi Negocio** workflow:

- Google login (and optional account selector handling)
- Sidebar `Negocio` -> `Mi Negocio` expansion
- `Agregar Negocio` modal validation
- `Administrar Negocios` page validation
- `Información General`, `Detalles de la Cuenta`, and `Tus Negocios` validations
- Legal links:
  - `Términos y Condiciones`
  - `Política de Privacidad`
  - Handles same-tab and new-tab behavior
- Checkpoint screenshots and a final PASS/FAIL JSON report

## Run

```bash
cd /workspace/e2e
npm install
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm test
```

Notes:

- The test is environment-agnostic and does **not** hardcode any SaleADS domain.
- If Playwright starts on `about:blank`, `SALEADS_LOGIN_URL` is required.
- Selectors prefer visible text as requested.

## Artifacts

Playwright stores outputs under `test-results/`:

- Screenshots for each checkpoint
- `saleads-mi-negocio-report.json` with these fields:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
