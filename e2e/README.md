# SaleADS Mi Negocio E2E

This Playwright suite automates the full workflow requested for:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones + Política de Privacidad validation
- Screenshot capture at key checkpoints
- Final PASS/FAIL report generation

## Requirements

- Node.js 20+ (validated with Node 22)
- Playwright browser binaries installed

## Install

```bash
cd e2e
npm install
npx playwright install chromium
```

## Run

```bash
cd e2e
SALEADS_LOGIN_URL="https://<your-saleads-login-page>" npm run test:saleads-mi-negocio
```

Notes:

- The test does **not** hardcode any domain.
- Provide `SALEADS_LOGIN_URL` for any SaleADS environment (dev/staging/prod).
- If your runner already starts on the login page, the test can run without this variable.
- The flow attempts to select Google account `juanlucasbarbiergarzon@gmail.com` when the selector appears.

## Artifacts

By default, outputs are created in:

- `e2e/test-results/saleads-mi-negocio/`

Includes:

- Checkpoint screenshots
- Legal page screenshots
- `final-report.md` with PASS/FAIL per required field and captured final URLs

To customize artifact path:

```bash
SALEADS_EVIDENCE_DIR="/tmp/saleads-evidence" npm run test:saleads-mi-negocio
```
