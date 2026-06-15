# SaleADS - Mi Negocio full workflow E2E

This Playwright test automates the complete **Mi Negocio** workflow requested in `saleads_mi_negocio_full_test`.

## What it validates

1. Login with Google.
2. Sidebar + Mi Negocio menu expansion.
3. "Agregar Negocio" modal validation.
4. "Administrar Negocios" account page sections.
5. Informacion General block.
6. Detalles de la Cuenta block.
7. Tus Negocios block.
8. Terminos y Condiciones page (same tab or new tab).
9. Politica de Privacidad page (same tab or new tab).
10. Final PASS/FAIL report with captured URLs.

The test captures screenshots at all important checkpoints and generates a JSON final report in Playwright `test-results`.

## Environment variables

- `SALEADS_LOGIN_URL` (recommended): login URL for the current environment (dev/staging/prod).
- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.

No hardcoded SaleADS domain is used.

## Run

```bash
cd /workspace/e2e/saleads-mi-negocio
npx playwright install chromium
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm test
```

To run headed:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:headed
```
