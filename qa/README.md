# SaleADS Mi Negocio full workflow test

This folder contains an end-to-end Playwright test for:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Legal links (Términos y Condiciones / Política de Privacidad), including new-tab handling
- Final PASS/FAIL JSON report by requested section names

## Setup

```bash
cd qa
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd qa
SALEADS_START_URL="https://<current-saleads-environment>/login" npm test
```

Optional:

- `SALEADS_GOOGLE_EMAIL` (defaults to `juanlucasbarbiergarzon@gmail.com`)

## Outputs

- Screenshots at key checkpoints under `qa/test-results/...`
- HTML report under `qa/playwright-report`
- Final JSON report attachment file:
  - `saleads-mi-negocio-final-report.json`
