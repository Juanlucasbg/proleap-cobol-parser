# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright test for:

- Google login
- Mi Negocio menu validations
- Agregar Negocio modal validations
- Administrar Negocios account page validations
- Legal links validations (Terminos y Condiciones, Politica de Privacidad)
- Final PASS/FAIL report generation

## Prerequisites

1. Install dependencies:

```bash
npm install
```

2. Install browser binaries (once per environment):

```bash
npx playwright install chromium
```

## Run

Use a URL variable so the same test works across dev/staging/prod:

```bash
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm run test:mi-negocio
```

Optional headed mode:

```bash
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm run test:mi-negocio:headed
```

## Evidence

Screenshots and final report are generated under:

`artifacts/evidence/<timestamp>/`

The final report is saved as:

`artifacts/evidence/<timestamp>/final-report.json`
