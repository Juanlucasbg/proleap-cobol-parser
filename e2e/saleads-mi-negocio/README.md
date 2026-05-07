# SaleADS Mi Negocio full workflow test

This directory contains a Playwright end-to-end test for the complete **Mi Negocio** workflow:

- Login with Google
- Sidebar navigation checks
- Mi Negocio submenu checks
- Agregar Negocio modal checks
- Administrar Negocios page checks
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones and Política de Privacidad checks (including new-tab handling)
- Final PASS/FAIL report generation

## Requirements

- Node.js 18+ (Node 22 is available in this environment)
- Playwright browser binaries installed:

```bash
npx playwright install chromium
```

## Environment variables

Set at least one of:

- `SALEADS_LOGIN_URL` (preferred, direct login page URL for the current environment)
- `SALEADS_BASE_URL` (fallback if login URL is not provided)

No hardcoded SaleADS domain is used by the test.

## Run

```bash
npm test
```

Optional headed mode:

```bash
npm run test:headed
```

## Artifacts

- Screenshots and report file are written under:
  - `test-results/saleads-mi-negocio-full/`
- Final step report:
  - `test-results/saleads-mi-negocio-full/final-report.json`
