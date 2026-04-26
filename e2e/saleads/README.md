# SaleADS E2E automation

This folder contains a standalone Playwright test for the workflow:

- `saleads_mi_negocio_full_test`

It is intentionally isolated from the Maven/Java project so browser automation can run without changing backend build behavior.

## What this test validates

The spec `tests/saleads_mi_negocio_full_test.spec.ts` executes:

1. Login with Google.
2. Open the **Mi Negocio** menu and validate submenu options.
3. Validate **Agregar Negocio** modal contents.
4. Open **Administrar Negocios** and validate account sections.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (new-tab or same-tab safe).
9. Validate **Política de Privacidad** (new-tab or same-tab safe).
10. Emit final PASS/FAIL report per requested report field.

Screenshots are captured at important checkpoints and stored under Playwright `test-results`.

## Environment compatibility

The test is environment-agnostic and does not hardcode a specific SaleADS domain.

- Preferred mode: start Playwright with the SaleADS login page already open.
- Fallback mode: set `SALEADS_START_URL` (or `SALEADS_URL`) to the login URL of the current environment.

## Run locally

```bash
cd e2e/saleads
npm install
npx playwright install --with-deps chromium
npm run test:mi-negocio
```

To run headed:

```bash
npm run test:headed -- tests/saleads_mi_negocio_full_test.spec.ts
```

## Output artifacts

- Screenshots: `test-results/**`
- HTML report: `playwright-report/`
- Final step report attachment: `final-report.json` per test run
