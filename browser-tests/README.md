# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic Playwright E2E test for the full **Mi Negocio** workflow:

- Google login
- Sidebar and Mi Negocio menu validation
- Agregar Negocio modal validation
- Administrar Negocios account page validation
- Información General, Detalles de la Cuenta, and Tus Negocios validation
- Términos y Condiciones / Política de Privacidad validation (same tab or new tab)
- Screenshot evidence and a final PASS/FAIL report JSON

## Requirements

- Node.js 18+

## Install

```bash
cd browser-tests
npm install
npm run install:browsers
```

## Run

Set the current environment login URL at runtime (no URL is hardcoded in the test):

```bash
cd browser-tests
SALEADS_START_URL="https://your-current-saleads-environment/login" npm run test:mi-negocio
```

## Artifacts

Evidence is saved under:

- `browser-tests/screenshots/*.png`
- `browser-tests/screenshots/saleads-mi-negocio-report-<timestamp>.json`

The JSON report contains:

- PASS/FAIL status for each requested validation field
- error details when a validation fails
- legal page final URLs
- checkpoint screenshot paths
