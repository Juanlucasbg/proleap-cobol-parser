# SaleADS Mi Negocio E2E

This folder contains the Playwright end-to-end workflow test:

- `saleads_mi_negocio_full_test`

The test validates:

1. Login with Google.
2. Sidebar and `Mi Negocio` expansion.
3. `Agregar Negocio` modal contents.
4. `Administrar Negocios` account view.
5. `Información General`, `Detalles de la Cuenta`, `Tus Negocios`.
6. `Términos y Condiciones` and `Política de Privacidad` pages (same tab or new tab), including URL capture.
7. Final PASS/FAIL report attachment.

## Environment-agnostic behavior

The test is intentionally domain-agnostic and does not hardcode a SaleADS URL.

- If the browser is already on a SaleADS login page, the test continues from there.
- If the browser starts on `about:blank`, define one of:
  - `SALEADS_URL`
  - `SALEADS_BASE_URL`

## Prerequisites

- Node.js 20+ (Node 22 is supported)
- A Google account option visible for login
- Playwright browser binaries installed

## Install

```bash
npm install
npx playwright install
```

## Run

Headless:

```bash
npm test
```

Headed:

```bash
HEADED=true npm test
```

With explicit environment URL:

```bash
SALEADS_URL="https://<your-saleads-host>" npm test
```

Optional account override:

```bash
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" npm test
```

## Evidence and report

Artifacts are written under:

- `test-results/` (screenshots/traces/videos)
- `playwright-report/` (HTML report)

The test always attaches `final-report.json` with per-step PASS/FAIL, captured legal URLs, and failure details.
