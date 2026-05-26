# SaleADS Mi Negocio Full Workflow Test

This Playwright suite validates the complete **Mi Negocio** workflow, including:

- Google login
- Sidebar navigation (`Negocio` -> `Mi Negocio`)
- `Agregar Negocio` modal validation
- `Administrar Negocios` account sections
- Legal links (`Términos y Condiciones` and `Política de Privacidad`) with new-tab support
- Screenshots at key checkpoints
- Final PASS/FAIL JSON report for each required validation field

## Requirements

- Node.js 18+
- Playwright browsers installed

## Setup

```bash
cd ui-tests/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

Provide an environment-agnostic start URL at runtime:

```bash
SALEADS_START_URL="https://<current-environment-login-url>" npm test
```

Alternative variables supported by the suite:

- `SALEADS_LOGIN_URL`
- `BASE_URL`

Headed mode (useful for observing Google login):

```bash
SALEADS_START_URL="https://<current-environment-login-url>" npm run test:headed
```

## Output Evidence

- Checkpoint screenshots are saved in Playwright test output folders.
- Full HTML report:

```bash
npm run report
```

- Final structured report file:
  - `saleads-mi-negocio-report.json`
  - Contains PASS/FAIL for:
    - Login
    - Mi Negocio menu
    - Agregar Negocio modal
    - Administrar Negocios view
    - Información General
    - Detalles de la Cuenta
    - Tus Negocios
    - Términos y Condiciones
    - Política de Privacidad
