# SaleADS Mi Negocio Full Workflow Test

This module contains the Playwright E2E test:

- `saleads_mi_negocio_full_test`

The test validates the complete **Mi Negocio** workflow, not only login:

1. Login with Google
2. Expand Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones
9. Validate Politica de Privacidad
10. Generate a final PASS/FAIL report

## Environment-agnostic setup

No hardcoded SaleADS domain is used.

Provide the current environment URL through environment variables:

- `SALEADS_LOGIN_URL` (preferred when login page URL is known), or
- `SALEADS_BASE_URL`

Optional:

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_EXPECTED_USER_NAME`
- `SALEADS_EXPECTED_USER_EMAIL` (default: same as `SALEADS_GOOGLE_EMAIL`)
- `PW_HEADLESS` (`false` for headed mode)

## Install and run

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm test
```

Headed mode:

```bash
PW_HEADLESS=false SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:headed
```

## Evidence and report outputs

Each run creates:

- Screenshots at required checkpoints
- `final-report.json` with PASS/FAIL per requested field
- Captured final URLs for legal pages

Artifacts are saved under:

- `artifacts/saleads_mi_negocio_full_test/<timestamp>/`
