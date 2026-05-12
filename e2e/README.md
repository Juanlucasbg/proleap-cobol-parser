# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright test for the full **Mi Negocio** workflow:

- Google login
- Sidebar navigation checks
- Mi Negocio menu checks
- Agregar Negocio modal checks
- Administrar Negocios page checks
- Legal links checks (including new-tab handling)
- Checkpoint screenshots and a final PASS/FAIL JSON report

## Run

1. Install dependencies:

```bash
npm install
```

2. Install Chromium for Playwright (first run only):

```bash
npx playwright install chromium
```

3. Execute the test against any SaleADS environment (no hardcoded URL):

```bash
SALEADS_LOGIN_URL="https://your-environment-host/login" npm run test:e2e
```

Optional:

- `HEADLESS=false` to run headed mode
- `npm run test:e2e:headed` for headed execution

## Evidence

- Checkpoint screenshots are attached to the Playwright output.
- Final report JSON is saved as:
  - `test-results/**/e2e-artifacts/saleads-mi-negocio-final-report.json`
