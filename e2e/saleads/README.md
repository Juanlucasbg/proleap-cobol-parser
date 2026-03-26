# SaleADS Mi Negocio - Full Workflow E2E

This folder contains the Playwright automation for:

- `saleads_mi_negocio_full_test`

It validates the complete workflow requested:

1. Login with Google (do not stop after login)
2. Open **Mi Negocio** and validate submenu
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones** (same tab or new tab)
9. Validate **Política de Privacidad** (same tab or new tab)
10. Produce PASS/FAIL final report JSON

## Environment-Agnostic Design

- No domain is hardcoded in test logic.
- The test starts from `SALEADS_BASE_URL` when needed.
- Selectors prioritize visible text.
- UI loading is awaited after each click/navigation.
- New tab handling is supported for legal links.

## Setup

```bash
cd /workspace/e2e/saleads
npm install
npx playwright install --with-deps
```

## Run

```bash
cd /workspace/e2e/saleads
SALEADS_BASE_URL="https://<your-saleads-environment>" ./scripts/run-saleads-mi-negocio.sh
```

Optional variables:

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_TEST_BUSINESS_NAME` (default: `Negocio Prueba Automatización`)
- `HEADLESS=false` to run headed

## Artifacts

Evidence is saved in:

- `artifacts/screenshots/` (dashboard, menu, modal, account page, legal pages)
- `artifacts/reports/saleads_mi_negocio_full_test-report.json` (PASS/FAIL matrix + legal final URLs)

