# SaleADS E2E - Mi Negocio workflow

This folder contains a Playwright end-to-end test for the full
`saleads_mi_negocio_full_test` workflow.

## What it validates

The test implements the complete flow requested:

1. Login with Google and verify app shell/sidebar.
2. Open `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Informacion General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Terminos y Condiciones` (new tab or same tab).
9. Validate `Politica de Privacidad` (new tab or same tab).
10. Produce a PASS/FAIL final report.

## Environment compatibility

- The test does **not** hardcode a specific SaleADS domain.
- It can run in any environment by providing `SALEADS_LOGIN_URL`.
- If `SALEADS_LOGIN_URL` is not set, the test assumes the page is already
  open on the SaleADS login screen (non-`about:blank`).

## Quick start

```bash
cd /workspace/e2e-saleads
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm test
```

Run headed mode:

```bash
HEADLESS=false SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:headed
```

## Evidence and report artifacts

- Checkpoint screenshots are captured at key milestones.
- Legal pages include screenshot + captured final URLs.
- A JSON attachment named `saleads-final-report` contains step-by-step PASS/FAIL.
- Artifacts are written under `e2e-saleads/artifacts/`.
