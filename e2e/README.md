# SaleADS E2E workflow tests

This folder contains the Playwright automation for the full **Mi Negocio** flow:

- Login with Google
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios view validation
- Información General, Detalles de la Cuenta, and Tus Negocios checks
- Términos y Condiciones + Política de Privacidad validation (including new-tab handling)
- Final PASS/FAIL report generation

## Test included

- `tests/saleads.mi-negocio.full.spec.js` (`saleads_mi_negocio_full_test`)

## Run

1. Install dependencies:

```bash
cd e2e
npm install
```

2. Run against any environment by setting a login URL dynamically (no hardcoded domain):

```bash
SALEADS_LOGIN_URL="https://<your-env>/login" npm run test:saleads-mi-negocio
```

Optional variables:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` to run with visible browser

## Evidence artifacts

Playwright output contains:

- Checkpoint screenshots for each critical step
- `final-report.json` with PASS/FAIL per required report field and captured legal URLs
