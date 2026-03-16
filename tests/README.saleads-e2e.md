# SaleADS Mi Negocio Full Workflow Test

This repository now includes an environment-agnostic Playwright test:

- `tests/saleads_mi_negocio_full_test.spec.js`

## What it validates

The test executes and validates the full workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google (or detect an already-authenticated session).
2. Open **Mi Negocio** menu.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (same tab or new tab).
9. Validate **Política de Privacidad** (same tab or new tab).
10. Generate final PASS/FAIL report.

The test captures screenshots at key checkpoints and writes a JSON report to:

- `artifacts/saleads_mi_negocio_full_test/final_report.json`

## Setup

Install dependencies:

```bash
npm install
npx playwright install
```

## Run

Set an environment URL without hardcoding a domain in test code:

```bash
export SALEADS_BASE_URL="https://<your-saleads-environment>"
```

Or:

```bash
export SALEADS_LOGIN_URL="https://<your-saleads-environment>/<login-path>"
```

Then run:

```bash
npm run test:saleads:mi-negocio
```

Headed mode:

```bash
npm run test:saleads:mi-negocio:headed
```

## Notes

- The test intentionally uses visible text selectors whenever possible.
- It always waits for UI load after clicks.
- Google account selection attempts to pick:
  - `juanlucasbarbiergarzon@gmail.com`
