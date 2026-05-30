# SaleADS Mi Negocio full workflow automation

This folder contains an environment-agnostic Playwright script for the workflow:

1. Login with Google
2. Open "Mi Negocio" menu
3. Validate "Agregar Negocio" modal
4. Open "Administrar Negocios"
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Generate final PASS/FAIL report per requested field

## Requirements

- Node.js 20+
- Playwright browsers installed:

```bash
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-saleads-login>" npm run test:saleads-mi-negocio
```

If `SALEADS_LOGIN_URL` is not set, the script expects the first browser tab in the persistent context to already be on the SaleADS login page.

You can also attach to an already opened browser (for orchestrators that preload the login tab) using CDP:

```bash
SALEADS_CDP_URL="http://127.0.0.1:9222" npm run test:saleads-mi-negocio
```

## Environment variables

- `SALEADS_LOGIN_URL` (optional): login URL for the current environment.
- `HEADLESS` (optional): set `false` to run headed.
- `SALEADS_USER_DATA_DIR` (optional): persistent browser profile path (default: `.saleads-browser-profile`).
- `SALEADS_ARTIFACTS_DIR` (optional): custom report/screenshots output directory.
- `PLAYWRIGHT_BROWSER_CHANNEL` (optional): browser channel.
- `SALEADS_LOCALE` (optional): locale, default `es-ES`.
- `SALEADS_CDP_URL` (optional): connect to an existing Chromium browser via CDP.

## Output artifacts

By default, output is generated at:

```text
artifacts/saleads_mi_negocio_full_test/<timestamp>/
```

Includes:

- `final-report.json`
- `final-report.md`
- `screenshots/*.png`
