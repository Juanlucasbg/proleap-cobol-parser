# SaleADS Mi Negocio full workflow test

This Playwright test automates the complete workflow requested for the **Mi Negocio** module:

1. Login with Google
2. Open and validate **Mi Negocio** menu
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Produce PASS/FAIL report with evidence

## Environment-agnostic behavior

- The test does not hardcode a domain.
- If `SALEADS_START_URL` is provided, it opens that URL.
- If `SALEADS_START_URL` is not provided, the test expects the browser to already be on the SaleADS login page.

## Run

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
npm run test:saleads-mi-negocio
```

Optional:

```bash
SALEADS_START_URL="https://<current-env-login-page>" npm run test:saleads-mi-negocio
HEADLESS=false npm run test:saleads-mi-negocio
```

## Outputs

Playwright outputs are generated under:

- `test-results/` (screenshots, traces, report attachments)
- `playwright-report/` (HTML report)

The test also writes:

- `saleads-mi-negocio-report.json`
- `saleads-mi-negocio-report-summary.txt`

Both files are attached to the Playwright result for quick inspection.
