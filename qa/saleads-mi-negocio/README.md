# SaleADS Mi Negocio Full Test

This folder contains an end-to-end Playwright test named `saleads_mi_negocio_full_test` that validates the **Mi Negocio** module workflow after Google login.

## Why this is environment-agnostic

- No domain is hardcoded.
- The login URL is provided at runtime via environment variables.
- Selectors prioritize visible text (`getByText`, `getByRole` names).

## Prerequisites

- Node.js 18+.
- Browser binaries installed for Playwright:

```bash
npx playwright install
```

## Run

From this folder:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-login-page>" npm test
```

Alternative variable names also supported by the test:

- `SALEADS_BASE_URL`
- `BASE_URL`

To run headed mode:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-login-page>" npm run test:headed
```

## Output evidence

The test captures:

- Checkpoint screenshots:
  - Dashboard after login
  - Expanded Mi Negocio menu
  - Crear Nuevo Negocio modal
  - Administrar Negocios page
  - Términos y Condiciones page
  - Política de Privacidad page
- Final structured report with PASS/FAIL for each required validation field.
- Final captured URLs for legal pages.

Playwright stores artifacts in its standard output directory (`test-results` / HTML report).
