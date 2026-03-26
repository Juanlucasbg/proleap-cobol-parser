# SaleADS Mi Negocio E2E

Playwright E2E test that validates the full **Mi Negocio** workflow after Google login, including:

- Login with Google (account selection fallback)
- Sidebar and Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page sections
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones validation (same tab or popup)
- Política de Privacidad validation (same tab or popup)
- Checkpoint screenshots and final JSON report

## Prerequisites

- Node.js 20+ (recommended)
- Playwright browsers installed

## Install

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Run

Option A: Provide login URL explicitly (recommended for CI):

```bash
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm test
```

Option B: If your runner opens the SaleADS login page before test start, run without URL:

```bash
npm test
```

Headed mode:

```bash
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:headed
```

## Artifacts

- Checkpoint screenshots: `test-results/checkpoints/`
- Step report JSON: `test-results/reports/saleads_mi_negocio_full_test.json`
- Playwright report: `playwright-report/`
