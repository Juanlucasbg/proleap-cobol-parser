# SaleADS Mi Negocio Full Workflow Test

This script automates the complete workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open and validate **Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Produce PASS/FAIL report for all required fields

It is environment-agnostic and does **not** hardcode any SaleADS domain.

## Prerequisites

```bash
python3 -m pip install playwright
python3 -m playwright install chromium
```

## Run

Use any SaleADS environment login URL:

```bash
python3 scripts/saleads_mi_negocio_full_test.py --base-url "https://<your-saleads-env>/login"
```

Or with environment variable:

```bash
export SALEADS_BASE_URL="https://<your-saleads-env>/login"
python3 scripts/saleads_mi_negocio_full_test.py
```

Optional flags:

- `--headless` run without visible browser window
- `--output-dir <path>` choose artifact folder
- `--timeout-ms <number>` default operation timeout

## Output Artifacts

Default artifact directory:

`artifacts/saleads_mi_negocio_full_test`

Includes:

- checkpoint screenshots (`*.png`)
- structured final report (`report.json`) with:
  - PASS/FAIL for each required report field
  - captured URLs for legal pages
  - screenshot references
