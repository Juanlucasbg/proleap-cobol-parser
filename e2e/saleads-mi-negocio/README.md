# SaleADS Mi Negocio Full Workflow Test

This suite implements `saleads_mi_negocio_full_test` using Playwright and validates the complete workflow:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal checks
4. Administrar Negocios page checks
5. Informacion General checks
6. Detalles de la Cuenta checks
7. Tus Negocios checks
8. Terminos y Condiciones validation (tab-aware)
9. Politica de Privacidad validation (tab-aware)
10. Final PASS/FAIL report generation

## Why this works across environments

- No hardcoded SaleADS domain is used.
- The target environment is provided at runtime.
- Locators prioritize visible UI text (`getByRole` / `getByText`) so the flow is resilient to URL differences.

## Runtime inputs

Optional (recommended for unattended runs): set one of these variables:

- `SALEADS_LOGIN_URL` (preferred)
- `SALEADS_APP_URL` (fallback)

If a URL is provided, the test opens it first. If no URL is provided, the suite assumes the browser is already on the SaleADS login page and continues from the current page.

## Install

```bash
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:saleads-mi-negocio
```

## Evidence and final report

On each run, Playwright stores artifacts under `test-results/`:

- Checkpoint screenshots:
  - `checkpoint-01-dashboard.png`
  - `checkpoint-02-mi-negocio-menu.png`
  - `checkpoint-03-agregar-negocio-modal.png`
  - `checkpoint-04-administrar-negocios.png`
  - `checkpoint-05-terminos-y-condiciones.png`
  - `checkpoint-06-politica-de-privacidad.png`
- Final structured report:
  - `saleads-mi-negocio-report.json`

The JSON report includes PASS/FAIL per requested field and the final URLs for legal pages.
