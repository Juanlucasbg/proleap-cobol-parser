# SaleADS Mi Negocio - Full Workflow E2E

This Playwright test validates the full SaleADS "Mi Negocio" workflow:

1. Login with Google
2. Open "Mi Negocio" menu
3. Validate "Agregar Negocio" modal
4. Open "Administrar Negocios"
5. Validate "Información General"
6. Validate "Detalles de la Cuenta"
7. Validate "Tus Negocios"
8. Validate "Términos y Condiciones"
9. Validate "Política de Privacidad"
10. Generate a PASS/FAIL report by step

## Run

```bash
SALEADS_URL="https://<your-saleads-environment>" npm run test:e2e
```

Optional variables:

- `SALEADS_BASE_URL`: alias for `SALEADS_URL`
- `GOOGLE_ACCOUNT_EMAIL`: defaults to `juanlucasbarbiergarzon@gmail.com`

## Evidence and report artifacts

The test stores evidence in Playwright output folders:

- checkpoint screenshots (`01-*.png`, `02-*.png`, ...)
- `final-report.json` with:
  - PASS/FAIL by requested step
  - captured final URLs for legal pages
  - validation failure details (if any)
