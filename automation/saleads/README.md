# SaleADS Mi Negocio Full Workflow Test

This folder contains the environment-agnostic automation script:

- `saleads_mi_negocio_full_test.py`

The script implements the requested flow end-to-end:

1. Login with Google.
2. Open `Mi Negocio` and validate submenu.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (same tab or new tab).
9. Validate `Política de Privacidad` (same tab or new tab).
10. Produce final PASS/FAIL report for all required fields.

## Environment-agnostic behavior

- No domain is hardcoded.
- Use `--url` to provide the login page URL for dev/staging/prod.
- Selectors prioritize visible text and role-based matching.
- The script waits for UI load after each click.
- Screenshots are captured at important checkpoints.
- For legal links, it supports both same-tab and new-tab navigation, captures final URL, and returns to app context.

## Prerequisites

- Python 3.10+ (tested with Python 3.12)
- Install dependencies:

```bash
pip3 install -r automation/saleads/requirements.txt
python3 -m playwright install chromium
```

## Run

```bash
python3 automation/saleads/saleads_mi_negocio_full_test.py \
  --url "https://<current-environment-login-url>"
```

Optional flags:

- `--account-email "juanlucasbarbiergarzon@gmail.com"`
- `--headed`
- `--slow-mo-ms 150`
- `--timeout-ms 25000`
- `--output-dir automation/artifacts/saleads_mi_negocio_full_test`

## Outputs

A timestamped directory is created under `automation/artifacts/saleads_mi_negocio_full_test/` with:

- Checkpoint screenshots (`*.png`)
- `final_report.json`
- `final_report.md`

Exit code:

- `0` if all required validations pass
- `1` if one or more required validations fail
