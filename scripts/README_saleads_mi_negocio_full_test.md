# SaleADS Mi Negocio Full Workflow Test

This script automates the full workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Expand **Mi Negocio** menu.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate **Informacion General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Terminos y Condiciones** (same tab or popup).
9. Validate **Politica de Privacidad** (same tab or popup).
10. Emit final PASS/FAIL report per section.

## Key properties

- Domain agnostic: no hardcoded SaleADS URL.
- Prefers visible text selectors.
- Waits after each click.
- Captures screenshots at important checkpoints.
- Handles new tab and same-tab legal links.
- Saves final URL for legal pages in the report.

## Install

```bash
python3 -m pip install -r scripts/requirements-saleads-e2e.txt
python3 -m playwright install chromium
```

## Run options

### A) Attach to an existing browser/tab already at login (recommended)

Start Chrome/Chromium with remote debugging enabled and open the SaleADS login page manually:

```bash
google-chrome --remote-debugging-port=9222
```

Then run:

```bash
python3 scripts/saleads_mi_negocio_full_test.py --cdp-url http://127.0.0.1:9222
```

### B) Launch a new browser and navigate via URL argument

```bash
python3 scripts/saleads_mi_negocio_full_test.py --headed --login-url "https://your-current-saleads-environment/login"
```

## Useful environment variables

- `SALEADS_LOGIN_URL`
- `CHROME_CDP_URL`
- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_TEST_BUSINESS_NAME`
- `SALEADS_TIMEOUT_MS`
- `SALEADS_EVIDENCE_ROOT`

## Outputs

Artifacts are saved under:

`artifacts/saleads_mi_negocio_full_test/<timestamp>/`

Includes:

- Checkpoint screenshots
- `report.json` with per-step status and details

