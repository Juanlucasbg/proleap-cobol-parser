# SaleADS E2E: Mi Negocio Full Workflow

This folder contains the Playwright automation for:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Informacion General / Detalles de la Cuenta / Tus Negocios checks
- Terminos y Condiciones and Politica de Privacidad link validation
- Checkpoint screenshots and final PASS/FAIL report

## Setup

```bash
cd /workspace/e2e
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd /workspace/e2e
SALEADS_LOGIN_URL="https://<your-saleads-login-page>" npm run test:saleads-mi-negocio
```

Optional env vars:

- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_EXPECTED_USER_NAME` (validated if provided)
- `BASE_URL` (fallback if `SALEADS_LOGIN_URL` is not set)

## Evidence output

After execution:

- Screenshots: `e2e/test-results/checkpoints/`
- Final workflow report: `e2e/test-results/saleads-mi-negocio-full-report.json`
- Playwright report: `e2e/playwright-report/`
