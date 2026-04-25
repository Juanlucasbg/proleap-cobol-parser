# SaleADS Mi Negocio full E2E test

This repository now includes a standalone Playwright test:

- `tests/saleads_mi_negocio_full_test.spec.js`

## Goal

Automate the full flow:

1. Login with Google.
2. Navigate to **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open and validate **Administrar Negocios** sections.
5. Validate legal links:
   - **Términos y Condiciones**
   - **Política de Privacidad**

It captures screenshots at key checkpoints and writes a final JSON report.

## Environment-agnostic execution

The test is designed to work in any SaleADS environment and does not hardcode a domain.

Provide the current environment login URL using one of:

- `SALEADS_BASE_URL` (preferred)
- `BASE_URL`
- `APP_URL`

Example:

```bash
SALEADS_BASE_URL="https://<current-saleads-env>/login" npm run test:saleads-mi-negocio
```

If no URL is provided and the browser starts at `about:blank`, the test fails fast with guidance.

## Commands

- Headless:

```bash
npm run test:saleads-mi-negocio
```

- Headed:

```bash
npm run test:saleads-mi-negocio:headed
```

## Artifacts

Outputs are saved under:

- `artifacts/saleads_mi_negocio_full_test/`

Including:

- Checkpoint screenshots
- `final_report.json` with PASS/FAIL per required section and captured legal URLs
