# SaleADS Mi Negocio E2E Test

This folder contains the Playwright test `saleads_mi_negocio_full_test.spec.ts`, which validates the full Mi Negocio workflow:

1. Google login
2. Sidebar > Negocio > Mi Negocio expansion
3. Agregar Negocio modal validation
4. Administrar Negocios sections
5. Información General checks
6. Detalles de la Cuenta checks
7. Tus Negocios checks
8. Términos y Condiciones validation (same tab or popup)
9. Política de Privacidad validation (same tab or popup)
10. Final PASS/FAIL report attachment

## Environment-agnostic behavior

- The test does **not** hardcode any SaleADS domain.
- If `SALEADS_LOGIN_URL` (or `SALEADS_BASE_URL`) is defined, it navigates there.
- Otherwise, it assumes the browser session already starts on a SaleADS login page.

## Run

```bash
npm install
npx playwright install --with-deps
npm run test:e2e -- e2e/tests/saleads_mi_negocio_full_test.spec.ts
```

## Evidence

The test captures screenshots at key checkpoints and adds a JSON attachment with final field-level PASS/FAIL results and captured legal-page URLs.
