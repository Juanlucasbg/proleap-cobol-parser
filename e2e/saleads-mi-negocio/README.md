# SaleADS Mi Negocio E2E

This folder contains a Playwright test that validates the full "Mi Negocio" workflow:

- Login with Google
- Mi Negocio sidebar expansion
- "Agregar Negocio" modal validation
- "Administrar Negocios" account page validation
- "Informacion General", "Detalles de la Cuenta", and "Tus Negocios" checks
- "Terminos y Condiciones" and "Politica de Privacidad" legal page checks
- Final PASS/FAIL report output

## Requirements

- Node.js 18+
- Browser binaries for Playwright:

```bash
npm run pw:install
```

## Environment variables

Set the target environment URL at runtime. This keeps the test domain-agnostic.

- `SALEADS_START_URL` or `SALEADS_LOGIN_URL`: login page URL for the current environment
- `SALEADS_GOOGLE_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`
- `HEADLESS` (optional): set `false` to run headed

Example:

```bash
SALEADS_START_URL="https://<current-env>/login" npm run test:mi-negocio
```

## Running

```bash
npm test
```

Or run only this test:

```bash
npm run test:mi-negocio
```

## Evidence and report

Playwright stores artifacts under `test-results/`.  
Checkpoint screenshots and the final JSON report are written under:

- `test-results/<test-name>/evidence/`
- Final report file: `10-final-report.json`
