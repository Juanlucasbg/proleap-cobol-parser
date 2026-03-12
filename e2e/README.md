# SaleADS Mi Negocio E2E

This folder contains a Playwright test that validates the complete Mi Negocio workflow:

- Google login
- Sidebar navigation to `Negocio` -> `Mi Negocio`
- `Agregar Negocio` modal validations
- `Administrar Negocios` account page validations
- Legal links (`Términos y Condiciones`, `Política de Privacidad`)
- Checkpoint screenshots and final PASS/FAIL report

## Run

1. Install dependencies (already done once if you cloned this branch):

```bash
cd /workspace/e2e
npm install
```

2. Install Playwright browsers:

```bash
npx playwright install
```

3. Run the workflow test against any environment:

```bash
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:saleads-mi-negocio
```

Notes:

- No fixed domain is hardcoded.
- `SALEADS_LOGIN_URL` (or `SALEADS_BASE_URL`) must be provided at runtime.
- The test will automatically click the Google account `juanlucasbarbiergarzon@gmail.com` if the chooser appears.
- A JSON final report is attached as `final-report.json` in test artifacts and printed in logs as `FINAL_REPORT`.
