# SaleADS Mi Negocio E2E

This folder contains an isolated Playwright test for the `saleads_mi_negocio_full_test` workflow:

1. Google login
2. `Negocio` > `Mi Negocio` menu validation
3. `Agregar Negocio` modal validation
4. `Administrar Negocios` page validation
5. `Información General`
6. `Detalles de la Cuenta`
7. `Tus Negocios`
8. `Términos y Condiciones` (including new-tab handling)
9. `Política de Privacidad` (including new-tab handling)
10. Final PASS/FAIL JSON report

## Why this works across environments

- No domain is hardcoded.
- The test can run either:
  - against an already-open SaleADS login page context, or
  - by setting `SALEADS_START_URL` to the login page of the current environment (dev/staging/prod).
- Selectors prioritize visible text labels (Spanish + English where relevant).

## Run

Install dependencies:

```bash
npm install
```

Install browser binaries (first run only):

```bash
npx playwright install chromium
```

Run headless:

```bash
npm run test:mi-negocio
```

Run headed:

```bash
npm run test:headed
```

If you want the test to navigate itself to login, set:

```bash
export SALEADS_START_URL="https://<current-env>/login"
```

## Evidence and report output

- Checkpoint screenshots: `test-results/checkpoints/`
- Final report: `test-results/saleads_mi_negocio_report.json`
