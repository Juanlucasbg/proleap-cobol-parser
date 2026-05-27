# SaleADS Mi Negocio full workflow test

This folder contains a Playwright E2E test for the full `Mi Negocio` workflow:

- Login with Google
- Open sidebar > `Negocio` > `Mi Negocio`
- Validate `Agregar Negocio` modal
- Validate `Administrar Negocios` page sections
- Validate legal links (`Términos y Condiciones` and `Política de Privacidad`)
- Produce a final PASS/FAIL report for each required checkpoint

## Environment-agnostic usage

Do **not** hardcode environment URLs. Use one of these approaches:

1. Provide `SALEADS_LOGIN_URL` directly:

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:e2e
```

2. Provide `SALEADS_BASE_URL` (+ optional path):

```bash
SALEADS_BASE_URL="https://<current-env>" SALEADS_LOGIN_PATH="/login" npm run test:e2e
```

## Commands

```bash
npm run test:e2e
npm run test:e2e:headed
```

## Evidence artifacts

Playwright output includes:

- Checkpoint screenshots (dashboard, expanded menu, modal, account page, legal pages)
- `saleads-mi-negocio-final-report.json` with:
  - PASS/FAIL by required field
  - legal final URLs
  - failure details (if any)
