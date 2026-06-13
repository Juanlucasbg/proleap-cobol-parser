# SaleADS Mi Negocio Full Workflow Test

This folder contains the automation script for:

- `saleads_mi_negocio_full_test`

## What it validates

The script executes and validates the complete workflow requested:

1. Login with Google
2. Open `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones`
9. Validate `Política de Privacidad`
10. Produce final PASS/FAIL report

It captures screenshots at key checkpoints and writes a JSON report with all results and legal final URLs.

## Environment-neutral behavior

No domain is hardcoded.

Use one of these modes:

- **Attach mode (recommended):**
  - Provide `PW_CDP_URL` to attach to an already opened Chromium page.
  - This supports the requirement *"assume the browser is already on the SaleADS login page"*.
- **Direct navigation mode:**
  - Provide `SALEADS_LOGIN_URL` and the script will open that URL.

## Run

```bash
npm run test:saleads-mi-negocio
```

### Common environment variables

- `PW_CDP_URL`: CDP endpoint for an existing Chromium browser session
- `SALEADS_LOGIN_URL`: login URL for the current SaleADS environment (required only if `PW_CDP_URL` is not set)
- `SALEADS_GOOGLE_ACCOUNT`: Google account to select (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_WAIT_TIMEOUT_MS`: timeout override per wait (default: `15000`)
- `HEADLESS`: set `false` to run headed in direct navigation mode

## Outputs

Artifacts are written under:

```text
artifacts/saleads_mi_negocio_full_test/<timestamp>/
```

Including:

- checkpoint screenshots (`.png`)
- `report.json` with summary per required report field
