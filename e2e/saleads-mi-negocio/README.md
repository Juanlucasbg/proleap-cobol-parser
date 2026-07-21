# SaleADS Mi Negocio Full Workflow Test

This package contains an end-to-end Playwright test named:

- `saleads_mi_negocio_full_test`

It validates the complete **Google login + Mi Negocio** workflow using visible-text selectors and checkpoint screenshots.

## What this test validates

1. Google login flow
2. Mi Negocio menu expansion
3. "Agregar Negocio" modal content
4. "Administrar Negocios" account page sections
5. "Información General"
6. "Detalles de la Cuenta"
7. "Tus Negocios"
8. "Términos y Condiciones" navigation (same tab or new tab)
9. "Política de Privacidad" navigation (same tab or new tab)
10. Final PASS/FAIL report in test output

## Environment-agnostic behavior

- No domain is hardcoded.
- Set the login URL at runtime with:
  - `SALEADS_LOGIN_URL` (preferred), or
  - `SALEADS_URL`, or
  - `BASE_URL`

## Run

```bash
cd e2e/saleads-mi-negocio
npm install
npm run install:browsers
SALEADS_LOGIN_URL="https://<your-saleads-login-page>" npm run test:headed
```

For CI/headless execution:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-login-page>" npm test
```

## Evidence generated

- Checkpoint screenshots are attached to Playwright results:
  - Dashboard loaded
  - Mi Negocio menu expanded
  - Agregar Negocio modal
  - Administrar Negocios page
  - Términos y Condiciones page
  - Política de Privacidad page
- Final URLs for legal pages are included in the final JSON report printed to stdout.
