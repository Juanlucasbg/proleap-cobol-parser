# SaleADS Mi Negocio E2E Test

This folder contains the automation `saleads_mi_negocio_full_test` implemented with Playwright.

## What it validates

1. Login with Google (including account selection for `juanlucasbarbiergarzon@gmail.com`).
2. Open **Negocio > Mi Negocio** and validate submenu options.
3. Validate **Agregar Negocio** modal elements and close with **Cancelar**.
4. Open **Administrar Negocios** and validate page sections.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (new tab or same tab) and capture final URL.
9. Validate **Política de Privacidad** (new tab or same tab) and capture final URL.
10. Emit final PASS/FAIL report.

## Environment-agnostic behavior

- No hardcoded SaleADS domain is used.
- If the current page is already the SaleADS login page, the test starts there.
- If browser starts on `about:blank`, you can provide an environment-specific URL:

```bash
export SALEADS_LOGIN_URL="https://<your-environment>/login"
```

## Run

```bash
npm run test:e2e
```

For headed mode:

```bash
HEADED=1 npm run test:e2e
```

## Evidence output

- Screenshots: `artifacts/screenshots/saleads_mi_negocio_full_test/<timestamp>/`
- Final JSON report: `artifacts/reports/`
