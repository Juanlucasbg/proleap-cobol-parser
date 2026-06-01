# SaleADS Mi Negocio Full Workflow Test

Automated Playwright script for validating the full **Mi Negocio** workflow in any SaleADS.ai environment.

## What it validates

The script executes and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones
9. Politica de Privacidad

It also writes screenshots for key checkpoints and produces a JSON report.

## Requirements

- Python 3.10+
- Playwright for Python
- Chromium browser binaries for Playwright

## Install

```bash
python3 -m pip install -r automation/requirements.txt
python3 -m playwright install chromium
```

## Run

Use any environment login URL (dev/staging/prod). The script does not hardcode a domain.

```bash
python3 automation/saleads_mi_negocio_full_test.py --base-url "https://<your-saleads-login-url>"
```

or with env var:

```bash
export SALEADS_BASE_URL="https://<your-saleads-login-url>"
python3 automation/saleads_mi_negocio_full_test.py
```

## Optional flags

- `--google-account "<email>"` (default: `juanlucasbarbiergarzon@gmail.com`)
- `--output-dir "<dir>"` (default: `artifacts/saleads_mi_negocio_full_test`)
- `--report-json "<path>"`
- `--timeout-ms 25000`
- `--headless`
- `--slow-mo-ms 150`

## Outputs

- Screenshots in the output directory
- Final report JSON (default):

```text
artifacts/saleads_mi_negocio_full_test/final_report.json
```
