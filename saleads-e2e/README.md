# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright test for the workflow:

- Login with Google
- Open `Negocio > Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate account sections and legal links
- Generate final PASS/FAIL report per required validation field

## Prerequisites

- Node.js 20+ (tested with Node 22)
- Chromium browser binaries for Playwright

## Install

```bash
cd /workspace/saleads-e2e
npm install
npm run install:browsers
```

## Run

Set the environment URL dynamically (dev/staging/prod), then execute the test:

```bash
cd /workspace/saleads-e2e
SALEADS_URL="https://<current-saleads-environment>" \
SALEADS_GOOGLE_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npm test
```

## Output Artifacts

The test writes artifacts into:

- `artifacts/screenshots/*.png` (checkpoint evidence)
- `artifacts/playwright-results.json` (Playwright JSON reporter output)
- `artifacts/saleads-mi-negocio-final-report.json` (final PASS/FAIL summary)

The final report fields are:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
