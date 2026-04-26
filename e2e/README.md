## SaleADS Mi Negocio Full Workflow E2E

This folder contains a Playwright end-to-end test for the workflow:

- Login with Google
- Navigate to **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Validate **Administrar Negocios** account sections
- Validate legal links:
  - **Términos y Condiciones**
  - **Política de Privacidad**

### Why it is environment-agnostic

The test does **not** hardcode any SaleADS domain. It reads the target login URL from environment variables.

### Run

1. Set the login page URL for your current environment:

```bash
export SALEADS_URL="https://<current-saleads-environment>/login"
```

2. (Optional) set the Google account to select:

```bash
export SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com"
```

3. Run:

```bash
npm run e2e:saleads:mi-negocio
```

For headed mode:

```bash
npm run e2e:saleads:mi-negocio:headed
```

### Evidence artifacts

The test writes artifacts to:

- `e2e/artifacts/screenshots/`
- `e2e/artifacts/reports/saleads-mi-negocio-final-report.json`
- `e2e/artifacts/reports/playwright-results.json`
- `e2e/artifacts/playwright-report/` (HTML report)

The final report JSON includes PASS/FAIL status per requested validation step and captured legal URLs.
