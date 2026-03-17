# SaleADS Mi Negocio full workflow test

This automation runs the full `saleads_mi_negocio_full_test` flow:

1. Login with Google.
2. Open `Negocio -> Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (including new-tab handling).
9. Validate `Política de Privacidad` (including new-tab handling).
10. Generate a PASS/FAIL final report.

The script does not hardcode any SaleADS domain and works across environments by configuration.

## Install

```bash
cd automation/saleads-mi-negocio
npm install
```

## Run

Use one of these environment-agnostic modes:

### Mode A: Navigate with URL

```bash
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:mi-negocio
```

### Mode B: Attach to an existing browser/tab already on login page

```bash
SALEADS_CDP_URL="http://127.0.0.1:9222" npm run test:mi-negocio
```

Optional:

- `HEADLESS=false` to run headed.

## Output artifacts

- `artifacts/final-report.json` (step-by-step PASS/FAIL summary)
- `artifacts/screenshots/*.png` (checkpoint screenshots)
