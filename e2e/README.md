# SaleADS Mi Negocio full workflow test

This folder contains a Playwright end-to-end test for the workflow:

- Login with Google
- Open `Negocio` -> `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate account sections
- Validate legal links (`Terminos y Condiciones`, `Politica de Privacidad`)
- Capture screenshots and final PASS/FAIL report

## Requirements

- Node.js 20+ recommended
- A reachable SaleADS login URL for the current environment (dev/staging/prod)

## Run

From repository root:

```bash
cd e2e
npm install
npx playwright install
SALEADS_URL="https://<your-current-saleads-environment>/login" npm run test:workflow
```

## Output evidence

- Checkpoint screenshots are saved to `e2e/artifacts/saleads-mi-negocio-full-test/`
- Playwright report is saved to `e2e/playwright-report/`
- The test attaches:
  - checkpoint screenshots
  - final legal URLs
  - `final-workflow-report.json` with PASS/FAIL by step
