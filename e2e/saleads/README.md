# SaleADS.ai - Mi Negocio Full Workflow (Playwright)

This suite validates the complete `saleads_mi_negocio_full_test` flow:

1. Login with Google.
2. Navigate to **Negocio -> Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Produce a PASS/FAIL report and screenshots for key checkpoints.

## Why it works across environments

- The test does **not** hardcode any domain.
- Start URL is provided dynamically with `SALEADS_LOGIN_URL`.
- Selectors prefer visible text labels (Spanish/English variants where relevant).

## Run

```bash
cd e2e/saleads
npm install
npx playwright install --with-deps
SALEADS_LOGIN_URL="https://<your-env>/login" npm test
```

Optional headed mode:

```bash
SALEADS_LOGIN_URL="https://<your-env>/login" npm run test:headed
```

## Artifacts

- Screenshots are attached to Playwright test output (`test-results`).
- HTML report is generated in `playwright-report`.
- Final structured report is attached as `saleads-mi-negocio-report.json`, including:
  - PASS/FAIL for each requested validation block
  - Final captured URLs for legal pages
