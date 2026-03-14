# SaleADS Mi Negocio E2E Suite

This folder contains the Playwright automation for:

- **`saleads_mi_negocio_full_test`**

It validates the complete flow requested for the SaleADS **Mi Negocio** module:

1. Login with Google
2. Open and validate Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Información General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Términos y Condiciones (including new-tab handling)
9. Validate Política de Privacidad (including new-tab handling)
10. Produce final PASS/FAIL report

## Why this works across environments

- No hardcoded SaleADS domain is used.
- Provide the environment-specific login URL via `SALEADS_URL`.
- Selectors prioritize visible text and semantic roles.

## Setup

```bash
cd /workspace/saleads-e2e
npm install
npm run install:browsers
```

## Run

```bash
cd /workspace/saleads-e2e
SALEADS_URL="https://<your-environment-login>" npm run test:e2e
```

Optional variables:

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` to run headed

Example:

```bash
cd /workspace/saleads-e2e
SALEADS_URL="https://staging.example.com/login" \
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
HEADLESS=false \
npm run test:e2e:headed
```

## Evidence and report

Playwright output contains:

- Checkpoint screenshots for dashboard/menu/modal/account/legal pages
- `saleads_mi_negocio_full_test_report.json` with PASS/FAIL status per required field

