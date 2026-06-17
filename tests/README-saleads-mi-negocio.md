# SaleADS Mi Negocio Full Workflow Test

This Playwright test validates the full `Mi Negocio` workflow described in the automation prompt:

- Google login (and account selection when visible)
- Sidebar `Mi Negocio` expansion
- `Agregar Negocio` modal validations
- `Administrar Negocios` page validations
- `Información General`, `Detalles de la Cuenta`, and `Tus Negocios` checks
- `Términos y Condiciones` and `Política de Privacidad` checks (same tab or new tab)
- Checkpoint screenshots and final PASS/FAIL report

## Why this is environment-agnostic

No SaleADS domain is hardcoded. The test reads the target login page from environment variables:

- `SALEADS_LOGIN_URL` (preferred)
- `SALEADS_BASE_URL`
- `BASE_URL`

## Run

```bash
npm install
npx playwright install --with-deps
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:e2e:mi-negocio
```

Optional:

```bash
HEADLESS=false SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:e2e:mi-negocio
```

## Output

- Playwright HTML report in `playwright-report/`
- Checkpoint screenshots in test output artifacts
- `final-report.json` attachment with PASS/FAIL by required field and legal URLs
