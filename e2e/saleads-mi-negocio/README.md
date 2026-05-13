# SaleADS Mi Negocio Full Test

This folder contains the `saleads_mi_negocio_full_test` Playwright workflow for validating:

1. Login with Google.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal content.
4. Administrar Negocios account view.
5. Información General section.
6. Detalles de la Cuenta section.
7. Tus Negocios section.
8. Términos y Condiciones navigation.
9. Política de Privacidad navigation.
10. Final PASS/FAIL report generation.

## Environment-agnostic behavior

- No domain is hardcoded in the test.
- The test starts from the current browser page.
- If execution begins at `about:blank`, provide a login page URL through `SALEADS_LOGIN_URL`.

## Run

```bash
cd e2e/saleads-mi-negocio
npx playwright install chromium
npm test
```

### Useful variants

```bash
npm run test:headed
npm run test:list
npm run test:report
```

## Evidence and report

- Important checkpoints are captured as screenshots.
- Legal document URLs are captured.
- A structured final report is saved as `final-report.json` in test output artifacts.
