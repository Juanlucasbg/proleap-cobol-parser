# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright test for the full **Mi Negocio** workflow:

- Login with Google
- Sidebar > Negocio > Mi Negocio
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones and Política de Privacidad checks (same tab or new tab)
- Checkpoint screenshots and a final PASS/FAIL report

## Prerequisites

- Node.js 20+ (Node 22 recommended)
- Browser starts at the SaleADS login page **or** set an environment URL

## Install

```bash
cd e2e
npm install
npm run install:browsers
```

## Run

When your Playwright session already starts on the login page:

```bash
cd e2e
npm test
```

If you need to provide the current environment login URL dynamically:

```bash
cd e2e
SALEADS_LOGIN_URL="https://your-current-env.example.com/login" npm test
```

## Artifacts

Generated under `e2e/artifacts/`:

- `screenshots/*.png` (required workflow evidence)
- `saleads-mi-negocio-report.json` (step-by-step PASS/FAIL summary and legal URLs)

Playwright HTML report output is generated under `e2e/playwright-report/`.
