# SaleADS E2E - Mi Negocio full workflow

This folder contains a Playwright test named:

- `saleads_mi_negocio_full_test`

It validates the complete workflow requested for:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal validation
4. Administrar Negocios page validation
5. Informacion General validation
6. Detalles de la Cuenta validation
7. Tus Negocios validation
8. Terminos y Condiciones validation (including popup/new tab handling)
9. Politica de Privacidad validation (including popup/new tab handling)
10. Final PASS/FAIL report for each step

## Environment compatibility

- The test is environment-agnostic and does not hardcode a SaleADS domain.
- Pass the login URL of your current environment through `SALEADS_URL` (or `BASE_URL`).

## Run locally

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
SALEADS_URL="https://your-current-saleads-login-url" npm test
```

For headed mode:

```bash
SALEADS_URL="https://your-current-saleads-login-url" npm run test:headed
```

## Evidence generated

The test captures screenshots at important checkpoints and attaches evidence to Playwright results:

- Dashboard loaded
- Mi Negocio expanded menu
- Agregar Negocio modal
- Administrar Negocios full page
- Terminos y Condiciones page + final URL
- Politica de Privacidad page + final URL
- Failure screenshot per failed step
- Final JSON report with PASS/FAIL by step

Generated outputs:

- `e2e/test-results/`
- `e2e/playwright-report/`
