# SaleADS Mi Negocio E2E workflow

This suite implements the workflow `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open `Mi Negocio` menu.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (including possible new tab handling).
9. Validate `Política de Privacidad` (including possible new tab handling).
10. Emit final PASS/FAIL report for required fields.

## Key design constraints covered

- No hardcoded SaleADS domain.
- URL is supplied at runtime via environment variable.
- Element selection prioritizes visible text.
- Waits for UI load after each click.
- Captures screenshots at important checkpoints.
- Captures final URLs for legal pages.

## Prerequisites

- Node.js 20+ (tested with Node 22).
- Chromium browser for Playwright:

```bash
npm run install:browsers
```

## Install

```bash
cd e2e/saleads-mi-negocio
npm install
```

## Runtime configuration

Required for unattended execution:

- `SALEADS_LOGIN_URL` (or `SALEADS_BASE_URL`): login URL for the active environment.

Optional:

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_EXPECTED_USER_NAME` (if provided, user-name validation is strict)
- `HEADLESS=false` for headed mode

## Run

```bash
cd e2e/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<current-env>/login" npm test
```

Headed:

```bash
cd e2e/saleads-mi-negocio
HEADLESS=false SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:headed
```

## Artifacts

Generated under `e2e/saleads-mi-negocio/artifacts/`:

- Checkpoint screenshots (`*.png`)
- Playwright JSON report (`playwright-results.json`)
- HTML report (`html-report/`)
- Final structured status report (`final-report.json`) with:
  - PASS/FAIL for:
    - Login
    - Mi Negocio menu
    - Agregar Negocio modal
    - Administrar Negocios view
    - Información General
    - Detalles de la Cuenta
    - Tus Negocios
    - Términos y Condiciones
    - Política de Privacidad
  - Evidence screenshot paths
  - Legal page final URLs
