# SaleADS Mi Negocio Full Workflow Test

This module executes the `saleads_mi_negocio_full_test` workflow using Playwright.

## What it validates

1. Login with Google and dashboard/sidebar visibility.
2. `Mi Negocio` menu expansion.
3. `Agregar Negocio` modal content and controls.
4. `Administrar Negocios` sections.
5. `Información General`.
6. `Detalles de la Cuenta`.
7. `Tus Negocios`.
8. `Términos y Condiciones` legal page (same tab or new tab).
9. `Política de Privacidad` legal page (same tab or new tab).
10. Final PASS/FAIL report for each requested area.

## Configuration

No domain is hardcoded. Use environment variables so the same script works in dev/staging/prod.

- `SALEADS_LOGIN_URL` (optional): Login page URL of the current environment.
  - If omitted, the script expects an already-open login page in the persistent browser profile.
- `SALEADS_GOOGLE_ACCOUNT` (optional): Google account email to select.
  - Default: `juanlucasbarbiergarzon@gmail.com`
- `SALEADS_HEADLESS` (optional): `true` or `false` (default `false`)
- `SALEADS_USER_DATA_DIR` (optional): Persistent Chromium profile directory.
- `SALEADS_ARTIFACTS_DIR` (optional): Output directory for report/screenshots.

## Install

```bash
npm install
npx playwright install chromium
```

## Run

```bash
npm run test:mi-negocio
```

## Output

Each run creates:

- `artifacts/<test-name>-<timestamp>/report.json`
- `artifacts/<test-name>-<timestamp>/screenshots/*.png`

The JSON report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
