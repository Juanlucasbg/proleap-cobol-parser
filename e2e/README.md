## SaleADS Mi Negocio full test

This folder contains an end-to-end automation script for the workflow:

1. Login with Google.
2. Open **Mi Negocio** menu.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Produce a final PASS/FAIL report.

### Prerequisites

- Node.js 18+ (22+ recommended)
- Playwright Chromium runtime:

```bash
npx playwright install chromium
```

### Run

```bash
SALEADS_URL="https://<your-saleads-login-url>" npm run saleads:mi-negocio:test
```

Optional environment variables:

- `SALEADS_LOGIN_URL` (fallback if `SALEADS_URL` is not provided)
- `BASE_URL` (fallback if neither `SALEADS_URL` nor `SALEADS_LOGIN_URL` are provided)
- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_HEADLESS` (`true` by default; set to `false` to watch browser)
- `SALEADS_ARTIFACTS_DIR` (custom output folder)

### Output artifacts

- JSON report: `e2e/artifacts/saleads_mi_negocio_full_test/report.json`
- Screenshots: `e2e/artifacts/saleads_mi_negocio_full_test/screenshots/`

The report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
