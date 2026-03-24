# SaleADS E2E tests

This folder contains Playwright-based end-to-end tests for SaleADS workflows.

## Prerequisites

- Node.js 18+ (22+ recommended)
- Browser binaries installed for Playwright

Install browser binaries:

```bash
npx playwright install
```

## Install dependencies

```bash
cd e2e
npm install
```

## Environment variables

The test is environment-agnostic and can run in any SaleADS environment.

- `SALEADS_LOGIN_URL` (optional): preferred login URL to open before starting.
- `BASE_URL` (optional): fallback URL if `SALEADS_LOGIN_URL` is not provided.
- `PLAYWRIGHT_BASE_URL` (optional): secondary fallback.
- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_UI_TIMEOUT_MS` (optional): UI wait timeout (default `15000`).
- `SALEADS_LEGAL_WAIT_TIMEOUT_MS` (optional): legal-page wait timeout (default `45000`).
- `SALEADS_MODAL_FILL_NAME` (optional): `true`/`false` for optional modal typing step (default `true`).
- `HEADLESS` (optional): `true` or `false` (`true` by default).

Examples:

```bash
# Run against current default behavior (login page pre-opened)
npm test

# Run against a specific environment URL
SALEADS_LOGIN_URL="https://<your-saleads-environment>" npm test

# Run headed for local debugging
HEADLESS=false npm run test:headed
```

## Artifacts

Artifacts are generated under `e2e/artifacts/`:

- Step screenshots under `e2e/artifacts/screenshots/` (e.g. `01_dashboard_loaded.png`)
- Legal-page screenshots (e.g. `terminos_y_condiciones.png`, `politica_de_privacidad.png`)
- `e2e/artifacts/saleads_mi_negocio_full_test.report.json` with PASS/FAIL fields and legal final URLs

## Test implemented

- `tests/saleads_mi_negocio_full_test.spec.ts`
  - Logs in with Google flow support
  - Continues through Mi Negocio workflow validations
  - Handles legal links opening in current tab or new tab
  - Captures screenshots at required checkpoints
  - Generates final pass/fail report
