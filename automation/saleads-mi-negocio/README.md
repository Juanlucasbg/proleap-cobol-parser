## SaleADS Mi Negocio Full Test

This folder contains a standalone Playwright workflow validator named:

- `saleads_mi_negocio_full_test.mjs`

It automates the required end-to-end flow:

1. Login with Google.
2. Open **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal pages:
   - Términos y Condiciones
   - Política de Privacidad
7. Emit a final PASS/FAIL report for each requested field.

### Environment-agnostic execution

No domain is hardcoded. Provide the login page URL at runtime:

```bash
SALEADS_LOGIN_URL="https://<your-environment>/login" npm test
```

Or:

```bash
npm test -- --url="https://<your-environment>/login"
```

If you already have a persistent Playwright profile with a non-blank page loaded, the script can reuse that URL.

### Optional variables

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` to watch execution
- `PW_USER_DATA_DIR` to reuse browser profile/session

### Install

```bash
npm install
npx playwright install chromium
```

### Artifacts

Each run writes artifacts under:

- `artifacts/<timestamp>/final-report.json`
- checkpoint screenshots (`01-...png`, `02-...png`, etc.)

The process exits with code `1` if any validation fails.
