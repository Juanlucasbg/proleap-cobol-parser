# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic Playwright test for:

- Google login
- `Negocio` -> `Mi Negocio` navigation
- `Agregar Negocio` modal validations
- `Administrar Negocios` page validations
- Legal links (`Términos y Condiciones` and `Política de Privacidad`) including popup/new-tab handling
- Checkpoint screenshots and final PASS/FAIL JSON report

## 1) Install dependencies

```bash
cd e2e
npm install
npm run install:browsers
```

## 2) Run the test

Set a login URL for the current environment:

```bash
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm test
```

Notes:

- The test does **not** hardcode any SaleADS domain.
- If the browser already starts at login page (non-`about:blank`), the URL variable is optional.
- To run headed mode:

```bash
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm run test:headed
```

## 3) Evidence and report

Playwright output includes:

- Checkpoint screenshots
- HTML report
- `saleads-mi-negocio-final-report.json` attachment containing:
  - PASS/FAIL status per requested field
  - validation error details (if any)
  - legal page final URLs
  - screenshot file paths
