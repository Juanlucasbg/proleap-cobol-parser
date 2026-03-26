## SaleADS Mi Negocio E2E

Playwright test suite for validating the complete **Mi Negocio** workflow in SaleADS across environments (dev/staging/prod), without hardcoding any specific domain.

### Prerequisites

- Node.js 18+ and npm
- Playwright browsers installed

### Setup

```bash
cd e2e
npm install
npx playwright install
```

### Environment variables

Use one of these when the browser is not already on the login page:

- `SALEADS_URL`
- `BASE_URL`

If neither is set, the test assumes the page is already at the SaleADS login entrypoint.

### Run

```bash
cd e2e
npm test
```

Headed mode:

```bash
npm run test:headed
```

### Outputs

- Checkpoint screenshots: `e2e/artifacts/screenshots/`
- Final structured report: `e2e/artifacts/final-report.json`
- Playwright HTML report: `e2e/playwright-report/`

The final report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
