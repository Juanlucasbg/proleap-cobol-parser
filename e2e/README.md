# SaleADS Mi Negocio E2E test

This folder contains a Playwright test for the full **Mi Negocio** workflow:

- Login with Google
- Open and validate the **Mi Negocio** menu
- Validate the **Agregar Negocio** modal
- Validate **Administrar Negocios** sections
- Validate legal links (**Términos y Condiciones** and **Política de Privacidad**)
- Capture screenshots at key checkpoints
- Print a final PASS/FAIL report by validation area

## Environment-agnostic setup

The test does **not** hardcode any SaleADS domain.

Provide the login page URL from your target environment through env vars:

- `SALEADS_LOGIN_URL` (preferred)
- `SALEADS_URL` (fallback)
- `BASE_URL` (fallback)

Optional:

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` to run headed

## Install

```bash
cd /workspace/e2e
npm install
npm run pw:install
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:saleads-mi-negocio
```

Headed mode:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:saleads-mi-negocio:headed
```

## Evidence output

- Screenshots and artifacts are saved under Playwright `test-results`.
- HTML report is generated under `playwright-report`.
