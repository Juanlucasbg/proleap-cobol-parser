# SaleADS Mi Negocio full test

Playwright automation for the workflow:

- Login with Google
- Navigate to `Negocio` -> `Mi Negocio`
- Validate `Agregar Negocio` modal
- Validate `Administrar Negocios` account sections
- Validate legal links (`Términos y Condiciones`, `Política de Privacidad`)
- Produce screenshots and final PASS/FAIL report

## Requirements

- Node.js 20+ (tested with Node 22)
- Access to a SaleADS login URL for the target environment

## Setup

```bash
cd /workspace/qa/saleads-e2e
npm install
npm run install:browsers
```

## Run

```bash
cd /workspace/qa/saleads-e2e
SALEADS_LOGIN_URL="https://<environment-login-page>" npm test
```

Optional environment variables:

- `HEADLESS=false` to run headed
- `SLOW_MO_MS=300` to slow interactions for observation

## Output

Artifacts are saved in:

`qa/saleads-e2e/artifacts/<timestamp>/`

Each run generates:

- checkpoint screenshots (`.png`)
- `final_report.json` containing PASS/FAIL per required validation field and captured legal URLs
