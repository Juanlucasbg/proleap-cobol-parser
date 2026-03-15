# SaleADS Mi Negocio workflow test

This folder contains a Playwright end-to-end test that validates the full Mi Negocio workflow:

- Login with Google
- Sidebar navigation to Mi Negocio
- "Agregar Negocio" modal validation
- "Administrar Negocios" view validation
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones and Política de Privacidad checks, including new-tab handling
- Final PASS/FAIL report generation

## Requirements

- Node.js 18+ (Node 22 recommended)
- A reachable SaleADS login URL for the current environment

## Install

```bash
cd e2e
npm install
npm run install:browsers
```

## Run

Use an environment-specific URL (dev/staging/prod) without hardcoding domains in the test:

```bash
cd e2e
SALEADS_URL="https://<your-saleads-login-page>" npm run test:saleads
```

Run headed (helpful for Google auth/account selection):

```bash
cd e2e
SALEADS_URL="https://<your-saleads-login-page>" npm run test:headed
```

## Evidence and report

Playwright artifacts are written under:

- `e2e/test-results/`
- `e2e/playwright-report/`

The test includes:

- Checkpoint screenshots
- Legal-page final URLs
- `final-report.json` attachment containing PASS/FAIL per required field
