# SaleADS E2E Tests

This folder contains environment-agnostic Playwright tests for SaleADS workflows.

## Prerequisites

- Node.js 18+ and npm
- Access to a SaleADS environment and Google login permissions

## Setup

```bash
cd e2e
npm install
npx playwright install
```

## Run the Mi Negocio full workflow test

```bash
cd e2e
SALEADS_BASE_URL="https://<current-saleads-environment>" npm run test:saleads-mi-negocio
```

Notes:

- The test intentionally does not hardcode a domain.
- `SALEADS_BASE_URL` is optional only when the browser session is already opened on the login page by the runner.
- Screenshots and JSON report are attached in Playwright output under `e2e/test-results`.

## Generated evidence

The test captures:

- Screenshot after dashboard loads
- Screenshot of expanded Mi Negocio menu
- Screenshot of Agregar Negocio modal
- Screenshot of Administrar Negocios page
- Screenshot and final URL for Términos y Condiciones
- Screenshot and final URL for Política de Privacidad

Final status is emitted in:

- `saleads_mi_negocio_full_test_report.json`
