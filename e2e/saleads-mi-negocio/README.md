# SaleADS Mi Negocio Full Workflow Test

Playwright E2E test that validates the full **Mi Negocio** flow after Google login.

## What this test covers

- Login with Google and verify app shell is visible.
- Open **Negocio > Mi Negocio** and validate submenu options.
- Open **Agregar Negocio** modal and validate expected content/actions.
- Open **Administrar Negocios** and validate account sections.
- Validate:
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
- Validate legal links:
  - Términos y Condiciones
  - Política de Privacidad
- Handle same-tab or new-tab legal navigation.
- Capture screenshots at key checkpoints.
- Generate structured PASS/FAIL report artifact.

## Environment-agnostic behavior

- No domain is hardcoded.
- Provide current environment login URL at runtime:
  - `SALEADS_URL` (preferred), or
  - `BASE_URL`, or
  - `APP_URL`

## Install

```bash
npm install
```

## Run

Headless:

```bash
SALEADS_URL="https://<current-env-login-url>" npm run test:mi-negocio
```

Headed:

```bash
SALEADS_URL="https://<current-env-login-url>" npm run test:mi-negocio:headed
```

## Artifacts

Each execution writes artifacts under:

- `artifacts/<runId>/screenshots/*.png`
- `artifacts/<runId>/report.json`

`report.json` includes:

- PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
- Captured legal page final URLs.
