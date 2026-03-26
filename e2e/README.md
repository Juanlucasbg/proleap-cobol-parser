## SaleADS Mi Negocio E2E

This folder contains the Playwright test:

- `tests/saleads_mi_negocio_full_test.spec.js`

### What it validates

The test automates the full workflow requested for the Mi Negocio module:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones
9. Validate Politica de Privacidad
10. Produce final PASS/FAIL report

### Environment support

The test is environment-agnostic and does not hardcode any SaleADS domain.

- If `SALEADS_URL` (or `BASE_URL` / `APP_URL`) is provided, the test navigates to that URL.
- If no URL is provided, the test assumes the browser starts already on the SaleADS login page.

### Install and run

From repository root:

```bash
cd e2e
npm install
npx playwright install --with-deps
npm run test:mi-negocio
```

Optional (set target environment explicitly):

```bash
SALEADS_URL="https://your-saleads-environment" npm run test:mi-negocio
```

### Evidence

The test captures screenshots at key checkpoints and writes `final-report.md` as an attached artifact in Playwright test output.
