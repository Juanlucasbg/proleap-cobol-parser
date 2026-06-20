# SaleADS Mi Negocio E2E

Playwright test suite to validate the complete "Mi Negocio" workflow after Google login.

## What this covers

- Login with Google and dashboard/sidebar validation.
- "Mi Negocio" menu expansion checks.
- "Agregar Negocio" modal checks.
- "Administrar Negocios" page and section-level validations.
- "Términos y Condiciones" and "Política de Privacidad" validation, including new-tab handling.
- Checkpoint screenshots and final PASS/FAIL report attachment.

## Requirements

- Node.js 18+.
- Playwright browsers installed (`npx playwright install`).
- Access to a SaleADS environment login page.

## Run

```bash
cd saleads-e2e
npm install
npx playwright install chromium
SALEADS_URL="https://<your-environment-login-url>" npm test
```

## Notes

- No SaleADS domain is hardcoded. The environment is provided through `SALEADS_URL`.
- If Google account selection appears, the test selects `juanlucasbarbiergarzon@gmail.com`.
- The final report is attached to the test artifacts as `saleads-mi-negocio-final-report`.
