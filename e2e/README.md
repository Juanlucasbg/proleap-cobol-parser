# SaleADS Mi Negocio E2E

This suite validates the full "Mi Negocio" workflow in any SaleADS.ai environment without hardcoding a domain.

## Test covered

- Google login flow (including optional account picker selection).
- Sidebar navigation to **Mi Negocio**.
- **Agregar Negocio** modal validations.
- **Administrar Negocios** account page validations.
- Validation of:
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
- Screenshot checkpoints and final PASS/FAIL report.

## Prerequisites

1. Install dependencies:

```bash
npm install
```

2. Install Playwright browser binaries:

```bash
npx playwright install
```

## Run

Set the start URL to the login page of the target environment:

```bash
SALEADS_START_URL="https://<your-saleads-environment>/login" npm run test:e2e:saleads
```

Headed mode:

```bash
SALEADS_START_URL="https://<your-saleads-environment>/login" npm run test:e2e:saleads:headed
```

## Evidence and outputs

- Checkpoint screenshots are stored in Playwright `test-results`.
- HTML report is generated in `playwright-report`.
- Final structured result is attached as JSON (`saleads-mi-negocio-final-report`) with:
  - PASS/FAIL by required report field.
  - Final URLs for legal pages.
