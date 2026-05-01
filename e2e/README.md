# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic browser automation script:

- `saleads_mi_negocio_full_test.py`

It validates the full **Mi Negocio** workflow (not only login), captures
checkpoint screenshots, handles legal links that may open in a new tab, and
produces a final PASS/FAIL report per required section.

## Requirements

From repository root:

```bash
python3 -m pip install -r requirements-e2e.txt
python3 -m playwright install chromium
```

## Run

```bash
python3 e2e/saleads_mi_negocio_full_test.py --base-url "https://<current-environment-login-url>"
```

Optional flags:

- `--google-email`: Google account used if chooser appears (default:
  `juanlucasbarbiergarzon@gmail.com`)
- `--timeout-ms`: Action timeout (default `20000`)
- `--headed`: Run headed browser instead of headless
- `--slow-mo-ms`: Delay between browser actions
- `--artifacts-dir`: Output directory (default `e2e/artifacts`)

You can also use environment variables:

- `SALEADS_BASE_URL`
- `SALEADS_TIMEOUT_MS`
- `SALEADS_SLOW_MO_MS`
- `SALEADS_ARTIFACTS_DIR`

## Outputs

Per run, artifacts are created in:

`e2e/artifacts/saleads_mi_negocio_full_test/<timestamp>/`

Including:

- `screenshots/*.png` at important checkpoints
- `final_report.json` with:
  - overall status
  - PASS/FAIL for each required report field:
    - Login
    - Mi Negocio menu
    - Agregar Negocio modal
    - Administrar Negocios view
    - Información General
    - Detalles de la Cuenta
    - Tus Negocios
    - Términos y Condiciones
    - Política de Privacidad
  - legal page final URLs (for Términos and Política)

