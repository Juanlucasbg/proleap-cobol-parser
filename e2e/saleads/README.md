# SaleADS Mi Negocio Full Workflow Test

This Playwright test validates the full `saleads_mi_negocio_full_test` workflow:

1. Login with Google.
2. Open `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate:
   - `Información General`
   - `Detalles de la Cuenta`
   - `Tus Negocios`
6. Validate legal links:
   - `Términos y Condiciones`
   - `Política de Privacidad`
7. Emit a final PASS/FAIL report per required field.

## Environment-agnostic behavior

- The test does **not** hardcode a domain.
- Provide your target environment login URL through:
  - `SALEADS_BASE_URL` (preferred), or
  - `BASE_URL`

If the browser starts on `about:blank` and no URL is provided, the test fails with a clear setup error.

## Required account selector target

By default the test selects:

- `juanlucasbarbiergarzon@gmail.com`

Override with:

- `SALEADS_GOOGLE_ACCOUNT_EMAIL`

## Install and run

```bash
cd e2e/saleads
npm install
npx playwright install --with-deps chromium
SALEADS_BASE_URL="https://your-saleads-login-url" npm test
```

To run headed:

```bash
SALEADS_BASE_URL="https://your-saleads-login-url" npm run test:headed
```

## Artifacts

Playwright stores evidence in `test-results/`, including:

- Checkpoint screenshots at key moments.
- Video/trace on failure.
- JSON report: `mi-negocio-final-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
