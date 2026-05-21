# SaleADS Mi Negocio Full Workflow E2E

Playwright test that validates the full Mi Negocio flow, including:

- Login with Google
- Sidebar navigation / Mi Negocio expansion
- Agregar Negocio modal fields and controls
- Administrar Negocios account view sections
- Legal links (Términos y Condiciones / Política de Privacidad), including new-tab handling
- Screenshot capture at key checkpoints
- Final PASS/FAIL JSON report

## Setup

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Run

Set one of these variables to avoid hardcoding a domain:

- `SALEADS_LOGIN_URL`
- `SALEADS_START_URL`
- `BASE_URL`

Example:

```bash
SALEADS_LOGIN_URL="https://your-saleads-env.example/login" npm test
```

Headed mode:

```bash
HEADLESS=false SALEADS_LOGIN_URL="https://your-saleads-env.example/login" npm run test:headed
```

## Output

- HTML report: `playwright-report/`
- Test artifacts and screenshots: `test-results/`
- Structured validation status: `saleads-mi-negocio-report.json` (inside test output folder)
