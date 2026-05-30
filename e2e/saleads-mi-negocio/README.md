# SaleADS Mi Negocio full workflow test

This Playwright test automates the full SaleADS.ai "Mi Negocio" workflow requested in the task:

1. Login with Google
2. Expand and validate `Negocio` -> `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open and validate `Administrar Negocios`
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Produce a final PASS/FAIL report as a test attachment

## Environment-agnostic behavior

- The test does not hardcode a SaleADS domain.
- If the browser starts on `about:blank`, configure a URL with one of:
  - `SALEADS_URL`
  - `BASE_URL`

## Run

```bash
cd e2e/saleads-mi-negocio
npx playwright install chromium
npm test
```

Optional headed run:

```bash
npm run test:headed
```

## Evidence and artifacts

- Checkpoint screenshots are captured at the key workflow milestones.
- The final per-step report is attached as `final-report.json`.
- Final legal URLs are attached as:
  - `Términos y Condiciones-url.txt`
  - `Política de Privacidad-url.txt`
