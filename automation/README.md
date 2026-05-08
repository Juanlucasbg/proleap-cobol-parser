# SaleADS Mi Negocio Full Workflow Test

This folder contains an end-to-end automation script for the workflow named:

- `saleads_mi_negocio_full_test`

The script validates the full sequence requested:

1. Login with Google.
2. Open `Mi Negocio` menu.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (including URL capture).
9. Validate `Política de Privacidad` (including URL capture).
10. Produce PASS/FAIL report by section.

## Why it is environment-agnostic

- No domain or URL is hardcoded.
- You can pass any environment login page with `--base-url`.
- Selectors primarily use visible text (buttons, links, labels).
- Legal-link validation handles same-tab or new-tab navigation.

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r automation/requirements.txt
python -m playwright install chromium
```

## Run

```bash
python automation/saleads_mi_negocio_full_test.py \
  --base-url "https://<current-saleads-environment>/login" \
  --google-account "juanlucasbarbiergarzon@gmail.com" \
  --headed
```

If `--base-url` is omitted, the script expects to already be on the login page context.

## Output

- Screenshots are saved under: `artifacts/saleads_mi_negocio_full_test/`
- Final report JSON: `artifacts/saleads_mi_negocio_full_test/final_report.json`

The JSON report contains:

- PASS/FAIL for each required section.
- Evidence details (including screenshot paths and legal page URL).
- Overall status.
