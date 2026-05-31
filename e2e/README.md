# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic Playwright workflow for:

1. Google login.
2. Mi Negocio menu validation.
3. Agregar Negocio modal validation.
4. Administrar Negocios page and account sections validation.
5. Legal links (`Términos y Condiciones`, `Política de Privacidad`) validation.
6. Final PASS/FAIL report generation.

## Script

- `e2e/saleads-mi-negocio-full-test.js`

## Run

```bash
npm run test:saleads:mi-negocio
```

## Required runtime input

You must provide one of the following:

- `SALEADS_URL` (recommended for standalone execution).
- `BROWSER_CDP_URL` to attach to an already opened browser session/page.

If both are provided, the script will navigate to `SALEADS_URL`.

### Example (standalone)

```bash
SALEADS_URL="https://<your-environment-host>" npm run test:saleads:mi-negocio
```

### Example (attach to existing browser)

```bash
BROWSER_CDP_URL="http://127.0.0.1:9222" npm run test:saleads:mi-negocio
```

## Optional environment variables

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS` (`true` by default; set `false` for headed mode)
- `CLICK_SETTLE_MS` (default `1200`)
- `ARTIFACTS_DIR` (default: `artifacts/saleads_mi_negocio_full_test/<timestamp>`)

## Evidence output

The script writes:

- Checkpoint screenshots.
- `report.json` with:
  - PASS/FAIL per requested validation field.
  - Legal page final URLs.
  - Screenshot paths.

All artifacts are written under `ARTIFACTS_DIR`.
