# SaleADS Mi Negocio workflow automation

This folder contains an environment-agnostic Playwright script that validates the requested SaleADS.ai **Mi Negocio** workflow end-to-end, including legal-link navigation handling (new tab or same tab), screenshots, and a final PASS/FAIL report per requested field.

## 1) Install dependencies

```bash
cd /workspace/e2e
npm install
npx playwright install chromium
```

## 2) Run

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-page>" \
SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com" \
npm run test:saleads-mi-negocio
```

### Optional environment variables

- `SALEADS_HEADLESS` (default: `true`)  
  Set `false` to watch the browser.
- `SALEADS_STORAGE_STATE`  
  Path to a Playwright storage state file if reusing an authenticated session.

## 3) Artifacts

Each run writes output to:

```text
e2e/artifacts/<timestamp>/
```

Including:

- `screenshots/*.png` checkpoint screenshots
- `final-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
