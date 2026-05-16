# SaleADS Mi Negocio full workflow test

This repository now includes a Playwright E2E test that validates the full **Mi Negocio** workflow:

- Google login
- Sidebar + Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios page sections
- Informacion General / Detalles de la Cuenta / Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
- Final PASS/FAIL report for each requested validation

## Test file

- `tests/saleads-mi-negocio-full.spec.js`

## Environment configuration

The test intentionally does **not** hardcode any SaleADS URL.  
Provide the login page URL at runtime with one of these variables:

- `SALEADS_LOGIN_URL` (preferred)
- `SALEADS_BASE_URL`
- `BASE_URL`

Example:

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" npm run saleads:mi-negocio
```

## Important behavior implemented

- Uses visible text selectors first (`getByRole`, `getByText`, `getByLabel`).
- Waits for UI settling after every click (`domcontentloaded` + `networkidle` fallback + short pause).
- Handles legal links that open in either the same tab or a new tab.
- Captures checkpoint screenshots:
  - dashboard
  - expanded Mi Negocio menu
  - Crear Nuevo Negocio modal
  - Administrar Negocios page (full page)
  - Terminos y Condiciones page
  - Politica de Privacidad page
- Stores final machine-readable report as test attachment:
  - `saleads-mi-negocio-final-report.json`

## Optional Google account selection

The test attempts to auto-select:

- `juanlucasbarbiergarzon@gmail.com`

when the Google account chooser is shown.
