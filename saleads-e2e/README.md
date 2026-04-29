# SaleADS E2E - Mi Negocio Full Workflow

This folder contains a Playwright test that implements the full
`saleads_mi_negocio_full_test` workflow end-to-end.

## What it validates

1. Login with Google (starting from SaleADS login page)
2. Open sidebar `Negocio > Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Informacion General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Terminos y Condiciones` (tab or same-page navigation)
9. Validate `Politica de Privacidad` (tab or same-page navigation)
10. Emit PASS/FAIL final report (JSON)

## Setup

```bash
cd saleads-e2e
npm install
npm run install:browsers
```

## Run

The test is environment-agnostic. It does not hardcode any domain.

Use `SALEADS_BASE_URL` for the current environment login page:

```bash
SALEADS_BASE_URL="https://<your-current-saleads-environment>/login" npm test
```

Optional credentials / user selectors:

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_EXPECTED_BUSINESS_USAGE` (default: `Tienes 2 de 3 negocios`)

## Artifacts

- Screenshots:
  - `test-results/<test-name>/dashboard-loaded.png`
  - `test-results/<test-name>/mi-negocio-expanded.png`
  - `test-results/<test-name>/agregar-negocio-modal.png`
  - `test-results/<test-name>/administrar-negocios.png`
  - legal page screenshots
- Final machine-readable report:
  - `test-results/saleads_mi_negocio_full_test_report.json`
