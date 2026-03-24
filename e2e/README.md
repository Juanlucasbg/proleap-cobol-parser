# SaleADS Mi Negocio E2E

Playwright end-to-end suite for validating the full **Mi Negocio** workflow after Google login.

## What this test covers

The `saleads_mi_negocio_full_test` validates:

1. Login with Google (including account selection when shown).
2. Sidebar + `Negocio` > `Mi Negocio` menu expansion.
3. `Agregar Negocio` modal structure.
4. `Administrar Negocios` main sections.
5. `Información General`.
6. `Detalles de la Cuenta`.
7. `Tus Negocios`.
8. `Términos y Condiciones` (same tab or popup).
9. `Política de Privacidad` (same tab or popup).
10. Final PASS/FAIL report JSON attachment and log output.

The test captures screenshots on key checkpoints and stores legal-page URLs in the final report.

## Environment-agnostic execution

The test is URL-agnostic by design:

- If the browser context is already on SaleADS login, it uses the current page.
- Or provide a URL with one of:
  - `SALEADS_URL`
  - `BASE_URL`
  - `E2E_URL`

## Run locally

```bash
cd e2e
npx playwright install --with-deps chromium
SALEADS_URL="https://<your-environment-host>" npm test
```

Headed mode:

```bash
npm run test:headed
```

Open report:

```bash
npm run test:report
```
