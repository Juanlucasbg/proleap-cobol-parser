# SaleADS Mi Negocio Full Workflow Test

Playwright end-to-end test that validates the complete `Mi Negocio` workflow, including:

- Login with Google
- Sidebar and `Mi Negocio` menu expansion
- `Agregar Negocio` modal validations
- `Administrar Negocios` sections validations
- Legal links (`Términos y Condiciones`, `Política de Privacidad`) with new-tab handling
- Checkpoint screenshots and final PASS/FAIL report attachment

## Why this works in any SaleADS environment

The test does not hardcode any domain.  
Set the login URL for the target environment using `SALEADS_LOGIN_URL`.

## Prerequisites

- Node.js 18+
- Playwright browsers installed

## Install

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install
```

## Run

```bash
cd /workspace/e2e/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm test
```

Headed mode:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm run test:headed
```

## Output evidence

- Checkpoint screenshots are captured via `testInfo.outputPath(...)`
- Final report is attached as `final-report.json`
- Legal final URLs are attached as:
  - `terminos-final-url.txt`
  - `politica-final-url.txt`
