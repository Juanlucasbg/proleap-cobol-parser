# SaleADS Mi Negocio E2E

This folder contains a Playwright test that validates the full "Mi Negocio" workflow:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal checks
- Administrar Negocios page checks
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones and Política de Privacidad (same tab or new tab)
- Final PASS/FAIL JSON report

## Test file

- `tests/saleads_mi_negocio_full_test.spec.js`

## Run

```bash
cd /workspace/e2e
npm install
npx playwright install --with-deps chromium
SALEADS_BASE_URL="https://<current-saleads-environment-login>" npm test
```

If `SALEADS_BASE_URL` is not provided, the test assumes the page is already at the SaleADS login screen.
