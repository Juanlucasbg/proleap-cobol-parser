# SaleADS Mi Negocio Full Test

Playwright E2E automation for the workflow:

1. Login with Google
2. Open **Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones**
9. Validate **Política de Privacidad**
10. Generate final PASS/FAIL report

## Why this works in any environment

- It does **not** hardcode any SaleADS domain.
- The environment URL is injected at runtime through variables.
- Element targeting prefers visible text and ARIA roles.

## Setup

From this folder:

```bash
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-env-login-url>" \
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npx playwright test tests/saleads-mi-negocio.spec.js
```

Optional:

- `HEADLESS=false` to watch browser execution.

## Artifacts

Each run writes:

- Screenshots: `artifacts/screenshots-<timestamp>/`
- Final JSON report: `artifacts/final-report-<timestamp>.json`

The JSON report includes PASS/FAIL for all required fields and final URLs for legal links.
