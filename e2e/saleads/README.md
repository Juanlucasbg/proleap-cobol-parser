# SaleADS - Mi Negocio E2E

This suite implements `saleads_mi_negocio_full_test` with Playwright.

## What it validates

1. Login with Google
2. Mi Negocio sidebar expansion
3. Agregar Negocio modal content
4. Administrar Negocios page sections
5. Informacion General section
6. Detalles de la Cuenta section
7. Tus Negocios section
8. Terminos y Condiciones legal page (same tab or new tab)
9. Politica de Privacidad legal page (same tab or new tab)
10. Final PASS/FAIL report per section

The test captures screenshots at the required checkpoints and prints legal-page URLs in logs.

## Environment

Do not hardcode domains in the test. Provide the login URL from the target environment:

```bash
export SALEADS_URL="https://<your-environment>/login"
```

## Install and run

From `e2e/saleads`:

```bash
npm install
npm run pw:install
npm run test:headed
```

Optional:

```bash
HEADLESS=false npm test
```

## Notes

- Selectors prefer visible text and include regex fallbacks for minor UI copy differences.
- After every click, the test waits for UI stabilization.
- If legal links open in a new tab, the test validates the tab and returns to the app tab.
