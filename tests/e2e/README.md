# SaleADS Mi Negocio Workflow E2E

This suite contains the `saleads_mi_negocio_full_test` Playwright scenario.

## What it validates

1. Login with Google.
2. Navigate and expand **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal content.
4. Open **Administrar Negocios** and validate sections.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (same tab or new tab) and capture URL.
9. Validate **Política de Privacidad** (same tab or new tab) and capture URL.
10. Produce final PASS/FAIL report per requested field.

## Run

```bash
npm run e2e:install-browsers
SALEADS_LOGIN_URL="https://your-env-login-url" npm run e2e:mi-negocio
```

Optional environment variables:

- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_EXPECTED_USER_NAME` (optional explicit user-name assertion)
- `PW_HEADLESS` (`true` by default, set `false` for headed mode)

## Evidence output

- Checkpoint screenshots and report JSON:
  - `artifacts/saleads-mi-negocio/<timestamp>/`
  - `artifacts/saleads-mi-negocio/latest-final-report.json`
