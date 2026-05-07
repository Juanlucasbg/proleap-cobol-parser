# SaleADS Mi Negocio E2E

This folder contains a Playwright end-to-end test named `saleads_mi_negocio_full_test` that validates:

1. Login with Google.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal validation.
4. Administrar Negocios page and account sections.
5. Legal links (`Términos y Condiciones` and `Política de Privacidad`) including new-tab handling.
6. Final PASS/FAIL report with URLs for legal pages.

## Why this works across environments

- No domain is hardcoded.
- The test uses the current page if already open on the login screen.
- If execution starts on `about:blank`, provide the current environment URL through env vars.

## Setup

```bash
cd /workspace/saleads-e2e
npm install
npx playwright install --with-deps
```

## Run

If the browser/session already opens the SaleADS login page, run directly:

```bash
npm test
```

If you need to provide the login URL for the environment:

```bash
SALEADS_LOGIN_URL="https://your-current-saleads-environment/login" npm test
```

Useful options:

```bash
HEADLESS=false npm run test:headed
npm run test:report
```

## Evidence generated

- Manual checkpoint screenshots are attached during the run:
  - `01-dashboard-loaded.png`
  - `02-mi-negocio-menu-expanded.png`
  - `03-agregar-negocio-modal.png`
  - `04-administrar-negocios-full.png`
  - `08-terminos-y-condiciones.png`
  - `09-politica-de-privacidad.png`
- Final report attachment: `final-report.json` with PASS/FAIL for all required sections.
