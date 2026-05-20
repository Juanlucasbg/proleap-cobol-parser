# SaleADS Mi Negocio E2E

This folder contains an end-to-end Playwright test that validates the complete **Mi Negocio** workflow:

- Login with Google
- Open and validate Mi Negocio menu
- Validate Agregar Negocio modal
- Open and validate Administrar Negocios view
- Validate Información General, Detalles de la Cuenta and Tus Negocios
- Validate Términos y Condiciones and Política de Privacidad, including new-tab behavior
- Emit a final PASS/FAIL report with step-level results

## Test file

- `tests/saleads-mi-negocio-full-test.spec.ts`

## Environment-agnostic behavior

The test does **not** hardcode any SaleADS domain.

- Preferred: provide a login page via `SALEADS_LOGIN_URL`
- Alternatives: `SALEADS_URL` or `BASE_URL`
- If no URL is provided and browser starts on `about:blank`, the test fails fast with a clear message

## Run

```bash
cd e2e
npm install
npx playwright install --with-deps
npm run test:saleads
```

## Evidence and report

Playwright stores artifacts under `e2e/test-results/` for each run:

- checkpoint screenshots at required moments
- `saleads-mi-negocio-final-report.json` with PASS/FAIL per workflow section
