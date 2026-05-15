# SaleADS Mi Negocio Full Workflow Test

This module contains an end-to-end Playwright automation for:

1. Google login on SaleADS
2. Mi Negocio menu expansion
3. Agregar Negocio modal validation
4. Administrar Negocios page validation
5. Información General validation
6. Detalles de la Cuenta validation
7. Tus Negocios validation
8. Términos y Condiciones validation (same tab or new tab)
9. Política de Privacidad validation (same tab or new tab)
10. Final PASS/FAIL report output

## Environment agnostic behavior

- The script does **not** hardcode any SaleADS domain.
- Provide the environment URL with:
  - `SALEADS_URL`, or
  - `SALEADS_LOGIN_URL`, or
  - `BASE_URL`

## Setup

```bash
cd /workspace/qa/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

```bash
cd /workspace/qa/saleads-mi-negocio
SALEADS_URL="https://<current-saleads-environment>/login" \
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npm run test:saleads-mi-negocio
```

## Optional environment variables

- `HEADLESS` (default: `true`)
- `ACTION_TIMEOUT_MS` (default: `30000`)
- `OUTPUT_DIR` (default: `./artifacts`)

## Evidence and report

- Screenshots are captured at key checkpoints required by the workflow.
- A JSON final report is generated per run in the artifacts folder with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
