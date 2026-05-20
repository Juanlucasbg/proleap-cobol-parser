# SaleADS Mi Negocio E2E

This Playwright test validates the full `Mi Negocio` workflow end-to-end:

1. Login with Google
2. Expand `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate:
   - `Información General`
   - `Detalles de la Cuenta`
   - `Tus Negocios`
6. Validate legal links:
   - `Términos y Condiciones`
   - `Política de Privacidad`
7. Generate PASS/FAIL report for each required section

## Runtime requirements

- Node.js 20+
- A valid SaleADS URL from any environment (dev, staging, production)

## Install

```bash
cd qa/saleads-e2e
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
SALEADS_BASE_URL="https://<your-environment-url>" npm test
```

Optional headed mode:

```bash
SALEADS_BASE_URL="https://<your-environment-url>" npm run test:headed
```

## Outputs

- Checkpoint screenshots: `test-results/checkpoints/`
- Final JSON report: `test-results/saleads-mi-negocio-final-report.json`
- Playwright HTML report: `playwright-report/`
