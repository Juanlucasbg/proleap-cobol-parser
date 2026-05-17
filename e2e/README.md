# SaleADS Mi Negocio E2E

This directory contains an environment-agnostic Playwright test for validating
the SaleADS "Mi Negocio" workflow end-to-end.

## What this test covers

The test file `tests/saleads-mi-negocio-full.spec.js` validates:

1. Login with Google and dashboard/sidebar visibility.
2. Expansion of the `Mi Negocio` menu.
3. `Agregar Negocio` modal validations.
4. `Administrar Negocios` page sections.
5. `Informacion General` section.
6. `Detalles de la Cuenta` section.
7. `Tus Negocios` section.
8. `Terminos y Condiciones` link/content.
9. `Politica de Privacidad` link/content.
10. Final PASS/FAIL report generation.

The test captures screenshots at important checkpoints and writes:

- `final-report.json`
- `final-report.md`

under the Playwright test output artifacts folder.

## Usage

1. Install dependencies:

```bash
npm install
npx playwright install chromium
```

2. Run the workflow test:

```bash
SALEADS_LOGIN_URL="<login-page-url-for-current-environment>" npm run test:saleads-mi-negocio
```

## Notes

- The test intentionally does not hardcode any specific SaleADS domain.
- `SALEADS_LOGIN_URL` must point to the login page for the active environment.
- If Google account picker appears, the script attempts to select:
  `juanlucasbarbiergarzon@gmail.com`.
