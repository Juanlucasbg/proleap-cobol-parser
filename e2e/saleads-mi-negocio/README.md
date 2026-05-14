# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic Playwright test for the full `Mi Negocio` flow:

- Login with Google
- Expand `Negocio` -> `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate account sections and legal links
- Produce a PASS/FAIL final report per requested field

## Requirements

- Node.js 18+
- A valid SaleADS environment URL in `SALEADS_URL`
- A Google account session capable of selecting `juanlucasbarbiergarzon@gmail.com`

## Install

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd /workspace/e2e/saleads-mi-negocio
SALEADS_URL="https://<current-env-login-url>" npm test
```

For local interactive debugging:

```bash
cd /workspace/e2e/saleads-mi-negocio
SALEADS_URL="https://<current-env-login-url>" npm run test:headed
```

## Evidence

The test captures screenshots and a final markdown report as Playwright attachments:

- Dashboard loaded
- Mi Negocio expanded menu
- Agregar Negocio modal
- Administrar Negocios page (full page)
- Términos y Condiciones page
- Política de Privacidad page
- Final PASS/FAIL report
