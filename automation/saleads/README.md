# SaleADS Mi Negocio Full Workflow Test

This folder contains `saleads_mi_negocio_full_test.js`, a Playwright-based test runner for validating the full **Mi Negocio** workflow:

1. Login with Google.
2. Expand **Mi Negocio** and validate submenu entries.
3. Validate **Agregar Negocio** modal fields and buttons.
4. Open **Administrar Negocios** and validate account sections.
5. Validate legal pages for **Términos y Condiciones** and **Política de Privacidad**.

## Requirements

- Node.js 18+ (22+ recommended).
- Playwright browsers installed.

## Install

```bash
cd automation/saleads
npm install
npx playwright install chromium
```

## Run

Choose one of these modes:

### A) Open login page from URL

```bash
SALEADS_LOGIN_URL="https://<your-saleads-login-page>" npm run saleads:mi-negocio
```

### B) Reuse an already-open browser session

```bash
BROWSER_WS_ENDPOINT="<cdp-endpoint>" npm run saleads:mi-negocio
```

## Optional environment variables

- `HEADLESS=false` to run headed.
- `SALEADS_KEEP_BROWSER_OPEN=true` to avoid auto-closing the browser.

## Output

Each run creates:

- `artifacts/<run-id>/report.json` (PASS/FAIL matrix + details)
- `artifacts/<run-id>/screenshots/*.png` (checkpoint evidence)

The report includes final URLs visited for:

- `Términos y Condiciones`
- `Política de Privacidad`
