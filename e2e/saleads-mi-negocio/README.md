# SaleADS Mi Negocio Full Workflow Test

Standalone Playwright automation for validating the full **Mi Negocio** workflow across any SaleADS.ai environment without hardcoding a specific domain.

## What it validates

The script executes and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

It also captures screenshots at key checkpoints and outputs a final JSON report including legal-page URLs.

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npm run install:browsers
cp .env.example .env
```

Set `SALEADS_LOGIN_URL` in `.env` to the login URL of the target environment.

## Run

Headed mode (recommended for Google SSO/account chooser):

```bash
npm run test:headed
```

Headless mode:

```bash
npm run test:headless
```

## Evidence and report

On each run, artifacts are created at:

```text
e2e/saleads-mi-negocio/artifacts/<timestamp>/
```

Contents:

- `screenshots/` checkpoint screenshots
- `final-report.json` final PASS/FAIL report + captured legal URLs

The script exits with code `1` if any validation fails.
