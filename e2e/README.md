# SaleADS Mi Negocio full workflow test

This folder contains a Playwright-based executable workflow:

- `saleads-mi-negocio-full-test.js`

It automates:

1. Google login
2. Mi Negocio menu checks
3. Agregar Negocio modal checks
4. Administrar Negocios page checks
5. Información General checks
6. Detalles de la Cuenta checks
7. Tus Negocios checks
8. Términos y Condiciones checks
9. Política de Privacidad checks

## Environment-agnostic execution

The script avoids hardcoded SaleADS domains.

Use one of these startup modes:

- Attach to an already-open browser/page:
  - `PLAYWRIGHT_WS_ENDPOINT=<ws-endpoint>`
- Start from a URL provided by environment:
  - `SALEADS_LOGIN_URL=<login-url>`

Optional:

- `HEADLESS=false` to run headed.
- `SALEADS_ARTIFACTS_DIR=<path>` to override output folder.

## Run

```bash
npm run saleads:mi-negocio
```

The runner writes:

- `report.json` with PASS/FAIL by requested report fields
- Checkpoint screenshots
- Captured legal page URLs
