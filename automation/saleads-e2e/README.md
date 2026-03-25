# SaleADS Mi Negocio E2E

Playwright test that validates the complete "Mi Negocio" workflow:

1. Login with Google
2. Open "Mi Negocio" menu
3. Validate "Agregar Negocio" modal
4. Open "Administrar Negocios"
5. Validate "Informacion General"
6. Validate "Detalles de la Cuenta"
7. Validate "Tus Negocios"
8. Validate "Terminos y Condiciones"
9. Validate "Politica de Privacidad"
10. Emit final PASS/FAIL report

## Install

```bash
cd /workspace/automation/saleads-e2e
npm install
npx playwright install --with-deps chromium
```

## Run

Set the login page URL for the current environment:

```bash
export SALEADS_LOGIN_URL="https://<current-saleads-env>/login"
npm test
```

Optional values:

- `SALEADS_LOGIN_URL`: login URL of current SaleADS environment. The test never hardcodes a domain.
- `SALEADS_GOOGLE_EMAIL`: preferred account in Google account picker. Default:
  `juanlucasbarbiergarzon@gmail.com`

For debugging:

```bash
npm run test:headed
```

## Evidence

Screenshots and reports are stored in:

- `artifacts/` (checkpoint screenshots + `final-report.json`)
- `test-results/` (failure artifacts from Playwright)
- `playwright-report/` (HTML execution report)

Named checkpoints in the test:

- 01-dashboard-loaded
- 02-mi-negocio-expanded
- 03-agregar-negocio-modal
- 04-administrar-negocios-view
- 08-terminos-y-condiciones
- 09-politica-de-privacidad
