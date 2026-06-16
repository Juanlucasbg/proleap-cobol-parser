# SaleADS Mi Negocio E2E

This Playwright suite validates the complete `saleads_mi_negocio_full_test` workflow:

1. Login with Google.
2. Open **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Generate final PASS/FAIL report.

## Environment-agnostic behavior

- No domain is hardcoded.
- Provide the login URL for the target environment through env vars:
  - `SALEADS_LOGIN_URL` (preferred)
  - `SALEADS_BASE_URL` (fallback)
  - `BASE_URL` (fallback)
- If a legal link opens in a new tab, the test validates it and returns to the app tab.
- If a legal link navigates in the same tab, the test navigates back to the account page.

## Commands

Install browsers once:

```bash
npx playwright install --with-deps chromium
```

Run test:

```bash
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:saleads-mi-negocio
```

Run headed mode:

```bash
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:saleads-mi-negocio:headed
```

## Artifacts

- Checkpoint screenshots: `e2e/artifacts/screenshots/`
- Final JSON report: `e2e/artifacts/report/saleads-mi-negocio-final-report.json`
