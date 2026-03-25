# SaleADS E2E tests

This folder contains an environment-agnostic Playwright test for the Mi Negocio workflow.

## Test included

- `tests/saleads-mi-negocio-full.spec.js`
  - Logs in with Google.
  - Navigates to `Negocio` -> `Mi Negocio`.
  - Validates:
    - Agregar Negocio modal.
    - Administrar Negocios page sections.
    - Informacion General.
    - Detalles de la Cuenta.
    - Tus Negocios.
    - Terminos y Condiciones.
    - Politica de Privacidad.
  - Captures screenshot evidence at key checkpoints.
  - Captures final URLs for legal pages.
  - Emits a final PASS/FAIL JSON report as an attachment.

## Requirements

- Node.js 18+.
- Playwright browser binaries installed (`npx playwright install --with-deps`).
- A valid authenticated Google account session may be required depending on environment security.

## Run

From this `e2e` folder:

```bash
npm install
npx playwright install --with-deps
npm run test:mi-negocio
```

Optional environment variables:

- `SALEADS_LOGIN_URL` (or `BASE_URL`): used only when the browser starts at `about:blank`.
- `SALEADS_BASE_URL`: sets Playwright `baseURL`.

## Output artifacts

- Screenshots: `test-results/screenshots/`
- JSON report: `test-results/results.json`
- Playwright HTML report: `playwright-report/`
