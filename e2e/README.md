# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright workflow test for:

- Login with Google
- Mi Negocio menu validation
- Agregar Negocio modal validation
- Administrar Negocios sections
- Legal links (Términos y Condiciones / Política de Privacidad)
- PASS/FAIL final report with screenshot evidence

## Requirements

- Node.js and npm
- Playwright browser binaries (`npx playwright install chromium`)
- Access to a SaleADS environment

## Environment variables

- `SALEADS_URL` (optional): current environment login URL.  
  If omitted, the test assumes the browser is already on the login page.
- `SALEADS_GOOGLE_ACCOUNT` (optional): Google account selector email.  
  Default: `juanlucasbarbiergarzon@gmail.com`
- `HEADLESS` (optional): set to `false` for headed mode.

## Run

```bash
npm run e2e:saleads:mi-negocio
```

Headed:

```bash
npm run e2e:saleads:mi-negocio:headed
```

## Artifacts

- `e2e/artifacts/saleads-mi-negocio/final-report.json`
- `e2e/artifacts/saleads-mi-negocio/final-report.md`
- `e2e/artifacts/saleads-mi-negocio/screenshots/*.png`
