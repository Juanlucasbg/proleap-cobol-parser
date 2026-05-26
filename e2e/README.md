# SaleADS Mi Negocio E2E

This folder contains a Playwright workflow test for:

- Google login
- Mi Negocio menu navigation
- Agregar Negocio modal validation
- Administrar Negocios sections validation
- Legal links validation (including URL capture and tab return)

## Test file

- `tests/saleads_mi_negocio_full_test.spec.ts`

## Setup

```bash
cd e2e
npm install
npm run install:browsers
```

## Run

Use any environment URL via env var (no hardcoded domain):

```bash
cd e2e
SALEADS_START_URL="https://<current-env>/login" npm run test:mi-negocio
```

Optional:

- `PW_HEADLESS=false` to run headed

## Outputs

- Checkpoint screenshots and report attachments in `e2e/test-results`
- HTML report in `e2e/playwright-report`
