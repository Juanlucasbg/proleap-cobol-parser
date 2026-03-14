# SaleADS Mi Negocio E2E

This folder contains a cross-environment Playwright test for the full Mi Negocio workflow:

- Login with Google
- Open Mi Negocio menu
- Validate "Agregar Negocio" modal
- Open "Administrar Negocios"
- Validate sections (Informacion General, Detalles de la Cuenta, Tus Negocios)
- Validate legal links (Terminos y Condiciones, Politica de Privacidad)
- Capture screenshots and final PASS/FAIL report

## Why this is environment-agnostic

- No hardcoded SaleADS domain is used.
- The test reads the URL from environment variables:
  - `SALEADS_URL` (preferred)
  - fallback: `BASE_URL` or `APP_URL`
- If no URL is provided, it expects a pre-opened page and fails fast with a clear message when still on `about:blank`.

## Setup

```bash
cd e2e
npm install
npm run install:browsers
```

## Run

```bash
cd e2e
SALEADS_URL="https://your-saleads-environment/login" npm run test:saleads-mi-negocio
```

Optional variables:

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` to run headed

## Evidence and report

- Playwright output: `e2e/test-results/`
- HTML report: `e2e/playwright-report/`
- Final JSON report: `e2e/artifacts/saleads_mi_negocio_full_test_report.json`
