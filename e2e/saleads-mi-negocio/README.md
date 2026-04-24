# SaleADS Mi Negocio E2E

Playwright suite for the full **Mi Negocio** workflow requested in automation task
`saleads_mi_negocio_full_test`.

## Goals covered

- Login with Google (continuing beyond login)
- Open and validate **Mi Negocio** menu
- Validate **Agregar Negocio** modal
- Open and validate **Administrar Negocios**
- Validate:
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
- Validate legal links:
  - Términos y Condiciones
  - Política de Privacidad
- Handle popup/new-tab or same-tab navigation
- Capture required screenshots and final URLs for legal pages
- Emit per-step PASS/FAIL final report as JSON

## Environment-agnostic behavior

This test does **not** hardcode any SaleADS domain. It assumes the browser is
already on the login page when running in persistent/manual mode.

Optionally, if `SALEADS_BASE_URL` is provided, the test can navigate there first.

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Configuration

Copy `.env.example` to `.env` and adjust if needed:

```bash
cp .env.example .env
```

Key variables:

- `SALEADS_BASE_URL` (optional): if set, test navigates to this URL first
- `SALEADS_GOOGLE_EMAIL` (optional): expected Google account in selector
- `HEADLESS`: `true` / `false`

## Run

```bash
npm run test:mi-negocio
```

Artifacts are written under `artifacts/`:

- Required screenshots
- `reports/saleads_mi_negocio_full_test.json`
- Playwright report files

