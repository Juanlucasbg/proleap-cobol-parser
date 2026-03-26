# SaleADS Mi Negocio E2E (Playwright)

This folder contains the automated workflow test requested as:

- `saleads_mi_negocio_full_test`

## Goal

Validate end-to-end flow:

1. Login with Google
2. Navigate to **Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate account sections and legal links
6. Produce PASS/FAIL report + screenshots

## Environment support

The test is environment-agnostic and does **not** hardcode a SaleADS domain.  
Pass the login page URL at runtime:

- `SALEADS_BASE_URL`
- or `SALEADS_URL`
- or `BASE_URL`

## Install

```bash
cd /workspace/e2e
npm ci
npm run test:install
```

## Run

```bash
cd /workspace/e2e
SALEADS_BASE_URL="https://<your-current-saleads-env-login>" npm test
```

## Outputs / Evidence

Generated under `e2e/artifacts/`:

- `01-dashboard-loaded.png`
- `02-mi-negocio-menu-expanded.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios-account-page.png`
- `05-terminos-y-condiciones.png`
- `06-politica-de-privacidad.png`
- `final-report.json` (PASS/FAIL by required fields + final legal URLs)
