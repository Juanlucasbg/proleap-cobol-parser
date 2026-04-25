# saleads_mi_negocio_full_test

Playwright end-to-end automation for validating the SaleADS.ai **Mi Negocio** module workflow across environments (dev/staging/production), without hardcoding any specific domain.

## What this test validates

The test executes and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

It also:

- waits for UI load after clicks,
- prefers visible-text selectors,
- supports legal links that open in the same tab or a popup tab,
- captures screenshots at required checkpoints,
- records final legal URLs,
- writes a final JSON report artifact.

## Prerequisites

- Node.js 18+ (or newer LTS)
- Chromium installed via Playwright

## Install

```bash
npm install
npm run install:browsers
```

## Run

### Option A: Browser already on login page

If your runner launches with a preloaded SaleADS login page, run:

```bash
npm run test:mi-negocio
```

### Option B: Provide a login URL through environment variable

```bash
SALEADS_LOGIN_URL="https://<your-saleads-login-page>" npm run test:mi-negocio
```

The test never asserts a fixed domain and remains environment-agnostic.

## Output evidence

- Checkpoint screenshots:
  - `test-results/screenshots/01-dashboard-loaded.png`
  - `test-results/screenshots/02-mi-negocio-menu-expanded.png`
  - `test-results/screenshots/03-agregar-negocio-modal.png`
  - `test-results/screenshots/04-administrar-negocios-view.png`
  - `test-results/screenshots/05-terminos-y-condiciones.png`
  - `test-results/screenshots/06-politica-de-privacidad.png`
- Final report attachment per Playwright run:
  - `saleads-mi-negocio-final-report.json`

