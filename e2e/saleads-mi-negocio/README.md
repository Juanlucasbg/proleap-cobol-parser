# SaleADS Mi Negocio E2E

Playwright end-to-end test for the `saleads_mi_negocio_full_test` workflow:

1. Login with Google.
2. Open **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (including new-tab handling).
9. Validate **Política de Privacidad** (including new-tab handling).
10. Emit final PASS/FAIL report.

## Environment agnostic design

- No hardcoded domain is used.
- The test uses environment variables:
  - `SALEADS_LOGIN_URL` (preferred)
  - `SALEADS_BASE_URL` (fallback)
- It selects elements by visible text where possible.

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
cp .env.example .env
```

Set `.env` values for your current environment.

## Run

```bash
npm test
```

Headed:

```bash
npm run test:headed
```

## Evidence and report

The test captures screenshots at key checkpoints and attaches:

- Dashboard loaded
- Mi Negocio expanded
- Agregar Negocio modal
- Administrar Negocios view
- Términos y Condiciones page
- Política de Privacidad page

It also writes `final-report.json` in Playwright output for CI artifact collection.
