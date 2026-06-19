# SaleADS Mi Negocio Full Workflow Test

This folder contains the automated end-to-end test:

- `tests/saleads-mi-negocio-full.spec.js`

## What it validates

The test implements the full `saleads_mi_negocio_full_test` workflow:

1. Login with Google
2. Open `Negocio` > `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones` (+ URL evidence)
9. Validate `Política de Privacidad` (+ URL evidence)
10. Attach final PASS/FAIL report

Screenshots are captured at key checkpoints and saved into Playwright test output.

## Environment-agnostic usage

No SaleADS domain is hardcoded in the test. You can run it against any environment.

If the test starts on `about:blank`, provide a login URL via environment variable:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm test
```

Optional:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)

## Run

```bash
cd e2e
npm install
npx playwright install --with-deps
npm run test
```

For a syntax/listing check only:

```bash
npm run test:list
```
