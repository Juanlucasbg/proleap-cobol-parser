# SaleADS Mi Negocio E2E

This folder contains a Playwright test that validates the complete **Mi Negocio** workflow in SaleADS.

## Covered workflow

The test file `tests/saleads-mi-negocio.spec.ts` validates:

1. Login with Google.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal.
4. Administrar Negocios page sections.
5. Información General.
6. Detalles de la Cuenta.
7. Tus Negocios.
8. Términos y Condiciones (new tab or same tab).
9. Política de Privacidad (new tab or same tab).
10. Final PASS/FAIL report by field.

## Environment agnostic behavior

- No hardcoded SaleADS domain is used.
- Set `SALEADS_URL` to the login URL for the current environment (dev/staging/production).
- Google account selector uses `SALEADS_GOOGLE_ACCOUNT` if provided, default:
  `juanlucasbarbiergarzon@gmail.com`.

## Installation

```bash
cd e2e
npm install
npm run pw:install
```

## Run

```bash
cd e2e
SALEADS_URL="https://your-saleads-login-url" npm run test:saleads-mi-negocio
```

Headed mode:

```bash
cd e2e
SALEADS_URL="https://your-saleads-login-url" npm run test:saleads-mi-negocio:headed
```

## Evidence and report

- Checkpoint screenshots are attached to the Playwright test output.
- Final JSON report is generated as `10-final-report.json` in Playwright test output and includes:
  - PASS/FAIL status for each required field.
  - Final URL captured for:
    - Términos y Condiciones
    - Política de Privacidad
