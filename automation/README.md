# SaleADS UI Automation

This directory contains environment-agnostic UI automation scripts.

## Workflow: `saleads_mi_negocio_full_test`

Script path:

`automation/scripts/saleads_mi_negocio_full_test.py`

### What it validates

1. Login with Google.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal fields and actions.
4. Administrar Negocios account page sections.
5. Informacion General section.
6. Detalles de la Cuenta section.
7. Tus Negocios section.
8. Terminos y Condiciones legal page and URL.
9. Politica de Privacidad legal page and URL.

It captures screenshots at key checkpoints and writes a final PASS/FAIL report.

### Environment-agnostic behavior

The script does not hardcode any SaleADS domain.
Provide the login URL of the current environment via an environment variable at runtime:

`SALEADS_START_URL`

### Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r automation/requirements.txt
python -m playwright install chromium
```

### Run

```bash
export SALEADS_START_URL="https://<current-saleads-login-url>"
python automation/scripts/saleads_mi_negocio_full_test.py --headless
```

Optional flags:

- `--google-account` (default: `juanlucasbarbiergarzon@gmail.com`)
- `--slow-mo-ms` (default: `150`)
- `--artifacts-dir` (default: `automation/artifacts/saleads_mi_negocio_full_test`)

### Output artifacts

- Final report:
  - `automation/artifacts/saleads_mi_negocio_full_test/final_report.json`
- Screenshots:
  - `automation/artifacts/saleads_mi_negocio_full_test/screenshots/*.png`

