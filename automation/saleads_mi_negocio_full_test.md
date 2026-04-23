# saleads_mi_negocio_full_test

End-to-end browser automation for the SaleADS.ai Mi Negocio workflow.

## Coverage

This test executes and validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal validation
4. Administrar Negocios page sections
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones (handles same-tab or new-tab navigation)
9. Politica de Privacidad (handles same-tab or new-tab navigation)
10. Final PASS/FAIL report by required fields

## Environment-agnostic behavior

- No domain is hardcoded.
- Provide the current environment login URL via:
  - `--url "<login-url>"`, or
  - `SALEADS_URL="<login-url>"`.
- If no URL is provided, the script exits with a clear FAIL report.

## Setup

Python 3.10+ is required.

Install Playwright:

```bash
pip3 install playwright
python3 -m playwright install chromium
```

## Run

```bash
python3 saleads_mi_negocio_full_test.py \
  --url "https://<your-saleads-environment>/login" \
  --headless false
```

Or using environment variables:

```bash
export SALEADS_URL="https://<your-saleads-environment>/login"
export HEADLESS="false"
python3 saleads_mi_negocio_full_test.py
```

## Outputs

Default evidence directory:

`artifacts/saleads_mi_negocio_full_test`

Generated artifacts:

- Checkpoint screenshots (`*.png`)
- Final JSON report: `final_report.json`

The JSON report includes:

- Overall status (`PASS` or `FAIL`)
- Field-level status for each required report field
- Notes for each validation
- Captured screenshot paths
- Final URL for legal page checks
