# SaleADS.ai - Mi Negocio Full Workflow E2E

This Playwright suite validates the full `Mi Negocio` workflow, including:

- Google login
- Sidebar expansion (`Negocio` -> `Mi Negocio`)
- `Agregar Negocio` modal validations
- `Administrar Negocios` account sections
- Legal links (`Términos y Condiciones`, `Política de Privacidad`) with URL capture
- Checkpoint screenshots and final PASS/FAIL JSON report

## Why this works in any environment

The test does not hardcode any domain. Set the target environment via environment variable:

- `SALEADS_BASE_URL` -> login page URL for the current environment (dev/staging/prod)

## Run

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps
SALEADS_BASE_URL="https://<your-saleads-login-url>" npm test
```

### Optional environment variables

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `PLAYWRIGHT_HEADLESS=false` to run headed

## Artifacts

Playwright outputs:

- Screenshots for requested checkpoints
- HTML report (`playwright-report/`)
- Structured workflow report attachment:
  - `saleads_mi_negocio_full_report.json`

The final JSON includes these required fields:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
