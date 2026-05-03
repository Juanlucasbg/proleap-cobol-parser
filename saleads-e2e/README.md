# SaleADS Mi Negocio full workflow test

This folder contains the Playwright implementation of `saleads_mi_negocio_full_test`.

## What this test validates

1. Login with Google.
2. Open `Mi Negocio` in sidebar and verify submenu.
3. Validate `Agregar Negocio` modal fields/actions.
4. Open `Administrar Negocios` and validate sections.
5. Validate `Informacion General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Terminos y Condiciones` (same tab or popup), capture screenshot + URL.
9. Validate `Politica de Privacidad` (same tab or popup), capture screenshot + URL.
10. Emit final PASS/FAIL report attachment.

The selectors are built to prefer visible text and avoid hard-coded domain assumptions.

## Run

Prerequisites:

- Node.js 18+
- Playwright Chromium browser (`npx playwright install chromium`)

Commands:

```bash
cd saleads-e2e
npm install
npx playwright install chromium
```

Run using the currently open login page context:

```bash
npm test
```

Optional: if the browser starts blank in your setup, provide login URL via env var:

```bash
SALEADS_URL="https://your-saleads-environment-url" npm test
```

## Artifacts

- Checkpoint screenshots are attached to test output:
  - `01-dashboard-loaded`
  - `02-mi-negocio-expanded`
  - `03-agregar-negocio-modal`
  - `04-administrar-negocios-page`
  - `08-terminos-y-condiciones`
  - `09-politica-de-privacidad`
- Final text attachment: `final-report` (PASS/FAIL per required step + legal URLs)
