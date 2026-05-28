# SaleADS Mi Negocio full workflow test

This Playwright E2E test validates the complete workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open `Negocio` > `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Produce a final PASS/FAIL report with captured legal URLs

## Requirements

- Node.js 18+ (tested with Node 22)
- Playwright Chromium browser

## Setup

```bash
npm install
npm run playwright:install
```

## Environment variables

- `SALEADS_URL` (required): login page URL of the target SaleADS environment (dev/staging/prod).
- `SALEADS_GOOGLE_EMAIL` (optional, default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_EXPECTED_USER_NAME` (optional, used for strict user name validation)

Example:

```bash
SALEADS_URL="https://<current-env>/login" npm run test:saleads-mi-negocio
```

## Evidence generated

- Checkpoint screenshots: `test-results/checkpoints/*.png`
- Playwright HTML report: `playwright-report/index.html`
- Final validation matrix attachment in test result:
  - `saleads-mi-negocio-final-report.json`
