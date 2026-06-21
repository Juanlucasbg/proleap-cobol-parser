# SaleADS Mi Negocio Full Test

Playwright suite for validating the full "Mi Negocio" workflow in any SaleADS.ai environment.

## Why this test is environment-agnostic

- No hardcoded SaleADS domain is used.
- The login entry point is injected through an environment variable.
- Selectors prioritize visible UI text (Spanish labels and section names).

## Preconditions

- A valid SaleADS login URL for the target environment (dev/staging/prod).
- Browser can complete Google authentication.
- The Google account `juanlucasbarbiergarzon@gmail.com` is available in account selector (or configure another email with `SALEADS_GOOGLE_ACCOUNT`).

## Install

```bash
cd e2e/saleads-mi-negocio
npm install
npm run install:browsers
```

## Run

```bash
cd e2e/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm run test:saleads-mi-negocio
```

Optional environment variables:

- `SALEADS_GOOGLE_ACCOUNT`: account email to click in Google selector.

## Evidence and report

The suite captures screenshots at key checkpoints:

1. Dashboard loaded after login
2. "Mi Negocio" menu expanded
3. "Crear Nuevo Negocio" modal
4. "Administrar Negocios" full page
5. "Términos y Condiciones" page
6. "Política de Privacidad" page

It also writes `saleads_mi_negocio_final_report.json` with PASS/FAIL per required field:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
