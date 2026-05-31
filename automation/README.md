# SaleADS automation

This folder contains standalone browser automation scripts that are environment-agnostic.

## `saleads_mi_negocio_full_test.py`

Validates the complete `Mi Negocio` workflow:

1. Login with Google.
2. Open `Mi Negocio` menu and verify submenu.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (new tab or same tab).
9. Validate `Política de Privacidad` (new tab or same tab).
10. Produce final PASS/FAIL report.

### Runtime requirements

- Python 3.10+
- Playwright for Python and Chromium browser binaries

Install dependencies:

```bash
python3 -m pip install -r automation/requirements.txt
python3 -m playwright install chromium
```

Run:

```bash
python3 automation/saleads_mi_negocio_full_test.py \
  --start-url "https://<current-environment>/login" \
  --headed \
  --slow-mo-ms 300
```

Outputs are written to:

- `automation/output/saleads_mi_negocio_full_test/*.png` (screenshots)
- `automation/output/saleads_mi_negocio_full_test/final_report.json` (step-level PASS/FAIL and legal URLs)
