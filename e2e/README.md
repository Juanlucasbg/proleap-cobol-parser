# SaleADS E2E: Mi Negocio Full Workflow

This suite contains the automated test:

- `saleads_mi_negocio_full_test`

It validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios account view
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones legal page/tab
9. Politica de Privacidad legal page/tab

It also captures screenshots at key checkpoints and emits a final JSON report with PASS/FAIL by requested section.

## Requirements

- Node.js 20+ recommended
- A reachable SaleADS environment
- Credentials/session allowing Google login flow

## Environment variables

- `SALEADS_BASE_URL` (recommended): Login page URL for the current environment.
  - Example: `https://<your-saleads-environment>/login`
  - The test is environment-agnostic and does not hardcode a domain.
- `SALEADS_GOOGLE_ACCOUNT` (optional):
  - Defaults to `juanlucasbarbiergarzon@gmail.com`
- `HEADLESS` (optional):
  - `true` (default) or `false`

## Install and run

```bash
cd e2e
npm install
npm run install:browsers
SALEADS_BASE_URL="https://your-env/login" npm test
```

Headed mode:

```bash
cd e2e
HEADLESS=false SALEADS_BASE_URL="https://your-env/login" npm run test:headed
```

## Artifacts

- Screenshots: `e2e/artifacts/*.png`
- JSON reporter output: `e2e/artifacts/playwright-results.json`
- HTML report: `e2e/playwright-report/`
- Playwright raw output: `e2e/test-results/`

