# SaleADS Mi Negocio full workflow test

This Playwright script automates the full `saleads_mi_negocio_full_test` flow:

1. Login with Google
2. Open `Negocio` -> `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate account sections
6. Validate legal links (`Términos y Condiciones`, `Política de Privacidad`)
7. Generate a final PASS/FAIL report by required fields

## Requirements

- Node.js 20+ recommended
- Chromium browser binary for Playwright

## Setup

```bash
cd e2e
npm install
npm run install:browsers
```

## Run

```bash
SALEADS_LOGIN_URL="https://<current-saleads-environment>/login" \
SALEADS_GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npm run test:saleads-mi-negocio
```

Optional:

- `HEADLESS=false` to run headed
- `SALEADS_ACTION_TIMEOUT_MS=15000`
- `SALEADS_NAV_TIMEOUT_MS=30000`

## Outputs

Each run writes artifacts under:

`e2e/artifacts/saleads-mi-negocio-<timestamp>/`

Including:

- checkpoint screenshots
- `final-report.json` with PASS/FAIL per required report field
