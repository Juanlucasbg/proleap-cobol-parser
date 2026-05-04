## SaleADS Mi Negocio E2E

This folder contains a Playwright end-to-end test that validates the full **Mi Negocio** workflow for SaleADS.ai across environments.

### Why this test is environment-agnostic

- It does **not** hardcode a specific domain.
- It opens the login page from one of these environment variables:
  - `SALEADS_URL`
  - `BASE_URL`
  - `TARGET_URL`
- Element selection prioritizes visible text and accessibility roles.

### Implemented workflow

The test `saleads_mi_negocio_full_test` validates:

1. Login with Google and dashboard/sidebar visibility.
2. Mi Negocio menu expansion and submenu items.
3. Agregar Negocio modal fields and buttons.
4. Administrar Negocios sections.
5. Información General section fields.
6. Detalles de la Cuenta section fields.
7. Tus Negocios section fields.
8. Términos y Condiciones page + URL capture.
9. Política de Privacidad page + URL capture.
10. Final step-level PASS/FAIL report JSON.

### Evidence and outputs

Generated artifacts are saved under `e2e/artifacts/`:

- Checkpoint screenshots
- `saleads_mi_negocio_report.json` with:
  - `results` (PASS/FAIL for each requested field)
  - `legalUrls` (captured final URLs)
  - `failures` (details when any step fails)

Playwright HTML report is generated in `e2e/playwright-report/`.

### Run

From repository root:

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
SALEADS_URL="https://<your-saleads-login-url>" npm test
```

Optional headed run:

```bash
SALEADS_URL="https://<your-saleads-login-url>" npm run test:headed
```

### Notes

- The test assumes the provided URL points to the current environment login page.
- If Google account selection appears, it attempts to select:
  `juanlucasbarbiergarzon@gmail.com`.
