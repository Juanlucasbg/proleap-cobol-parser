# SaleADS - Mi Negocio full workflow (Playwright)

This folder contains an end-to-end Playwright test that automates:

1. Login with Google.
2. Sidebar navigation to `Negocio -> Mi Negocio`.
3. Validation of `Agregar Negocio` modal.
4. Validation of `Administrar Negocios` sections.
5. Validation of legal links (`Terminos y Condiciones`, `Politica de Privacidad`) including popup/new-tab handling.
6. Final PASS/FAIL report generation per required checkpoint.

## Why this works in any environment

- No SaleADS domain is hardcoded.
- Runtime target is provided through environment variables.
- Selectors are primarily based on visible text.

## Prerequisites

- Node.js 18+ (recommended).
- Browsers installed for Playwright:

```bash
npx playwright install
```

## Run

```bash
cd qa/saleads-mi-negocio
SALEADS_URL="https://your-current-saleads-environment/login" npm test
```

### Optional environment variables

- `SALEADS_EXPECTED_GOOGLE_ACCOUNT`  
  Default: `juanlucasbarbiergarzon@gmail.com`
- `SALEADS_TEST_BUSINESS_NAME`  
  Default: `Negocio Prueba Automatizacion`
- `SALEADS_STORAGE_STATE`  
  Path to Playwright storage-state JSON if you want to skip interactive login.

## Artifacts

- Checkpoint screenshots are attached in Playwright output.
- Final JSON report is generated as:
  - `saleads-mi-negocio-final-report.json` (inside test output folder)
- Captured legal page URLs are printed to test logs with:
  - `[LEGAL_URL] Terminos y Condiciones: ...`
  - `[LEGAL_URL] Politica de Privacidad: ...`
