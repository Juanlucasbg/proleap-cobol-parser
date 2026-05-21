# SaleADS Mi Negocio Full Workflow E2E

This Playwright suite automates the workflow requested in `saleads_mi_negocio_full_test`:

- Login with Google
- Expand `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate account sections
- Validate `Términos y Condiciones` and `Política de Privacidad` (new-tab or same-tab)
- Produce PASS/FAIL final report and screenshot evidence

## Setup

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:mi-negocio
```

## Optional environment variables

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_EXPECTED_USER_NAME` (strict user-name validation in `Información General`)
- `HEADLESS=false` (run headed browser)

## Outputs

- Screenshots and final report JSON are written under:
  - `test-results/saleads-mi-negocio/<timestamp>/`
