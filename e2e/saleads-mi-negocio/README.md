# SaleADS Mi Negocio E2E

Playwright end-to-end test suite for validating the **Mi Negocio** workflow in any SaleADS.ai environment.

## Why this is environment-agnostic

- It does **not** hardcode a domain or URL.
- It expects the browser to already be on the login page, and also supports `SALEADS_BASE_URL` as a fallback when the page is blank.
- It relies primarily on **visible text selectors** and resilient waiting.

## Test included

- `saleads_mi_negocio_full_test`
  - Login with Google
  - Open Mi Negocio
  - Validate Agregar Negocio modal
  - Open Administrar Negocios
  - Validate:
    - Informacion General
    - Detalles de la Cuenta
    - Tus Negocios
  - Validate legal links:
    - Terminos y Condiciones
    - Politica de Privacidad
  - Capture screenshots at key checkpoints
  - Print PASS/FAIL final report in test output

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Optional environment variables

- `SALEADS_BASE_URL`: If set, test navigates to this URL first.
- `GOOGLE_ACCOUNT_EMAIL`: Defaults to `juanlucasbarbiergarzon@gmail.com`.
- `PWDEBUG=1`: Useful for interactive local debugging.

## Run

```bash
npm test
```

For local debugging:

```bash
npm run test:headed
```

## Artifacts

- Screenshots are saved under:
  - `e2e/saleads-mi-negocio/artifacts/screenshots/`
- Playwright HTML report:
  - `e2e/saleads-mi-negocio/playwright-report/`
