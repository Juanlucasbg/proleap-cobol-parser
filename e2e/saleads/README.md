# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic Playwright E2E test for:

`saleads_mi_negocio_full_test`

The scenario validates:

1. Google login and dashboard/sidebar visibility.
2. `Negocio` -> `Mi Negocio` menu expansion.
3. `Agregar Negocio` modal content.
4. `Administrar Negocios` account page sections.
5. `Información General`.
6. `Detalles de la Cuenta`.
7. `Tus Negocios`.
8. `Términos y Condiciones` navigation (same tab or new tab).
9. `Política de Privacidad` navigation (same tab or new tab).
10. A final PASS/FAIL report by required fields.

## Requirements

- Node.js 18+.
- Playwright browser binaries installed.

Install browser binaries (first time only):

```bash
npx playwright install
```

## Environment variables

No domain is hardcoded. Provide one of:

- `SALEADS_LOGIN_URL` (preferred), or
- `SALEADS_BASE_URL` (the test will navigate to `/login`).

Optional:

- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADED=true` to run with visible browser UI

Example:

```bash
SALEADS_LOGIN_URL="https://your-saleads-env.example.com/login" npm run test:saleads-mi-negocio
```

## Outputs

Artifacts are written under:

`test-results/saleads-mi-negocio/<timestamp>/`

Including:

- Checkpoint screenshots
- Legal pages screenshots
- `saleads-mi-negocio-final-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
