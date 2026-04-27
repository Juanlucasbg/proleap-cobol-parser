## SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright test for the SaleADS
Mi Negocio workflow.

### Test implemented

- `e2e/saleads-mi-negocio.spec.js`
  - Logs in with Google (assumes app is already on login page when no URL is provided)
  - Continues through Mi Negocio menu, Agregar Negocio modal, Administrar Negocios view
  - Validates Informacion General, Detalles de la Cuenta, Tus Negocios
  - Opens and validates Terminos y Condiciones and Politica de Privacidad
  - Handles same-tab/new-tab legal links and returns to app tab
  - Captures screenshots at key checkpoints
  - Emits PASS/FAIL summary in test output

### Running

1. Optional: provide login URL if your run does not start already on login page.
   - `export SALEADS_LOGIN_URL="https://your-env-url"`
2. Optional: set Google account email expected in selector.
   - `export SALEADS_GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"`
3. Run:
   - Headless: `npm run test:e2e:saleads-mi-negocio`
   - Headed: `npm run test:e2e:saleads-mi-negocio:headed`

### Artifacts

- Screenshots: `test-results/screenshots/`
- HTML report: `playwright-report/`
