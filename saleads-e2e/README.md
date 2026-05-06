# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright automation script for the workflow:

1. Login with Google
2. Open **Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open and validate **Administrar Negocios**
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Produce a final PASS/FAIL report per requested field.

## Requirements

- Python 3.10+
- Chromium dependencies available in the execution environment

## Installation

```bash
python3 -m pip install -r saleads-e2e/requirements.txt
python3 -m playwright install chromium
```

## Run

The script intentionally does **not** hardcode a domain. Provide the current environment login URL:

```bash
python3 saleads-e2e/saleads_mi_negocio_full_test.py --base-url "https://<current-saleads-login-url>"
```

or via environment variable:

```bash
SALEADS_BASE_URL="https://<current-saleads-login-url>" \
python3 saleads-e2e/saleads_mi_negocio_full_test.py
```

Use headed mode for visual debugging:

```bash
python3 saleads-e2e/saleads_mi_negocio_full_test.py \
  --base-url "https://<current-saleads-login-url>" \
  --headed
```

## Artifacts

Each run writes artifacts under:

```text
saleads-e2e/artifacts/<UTC_TIMESTAMP>/
```

Including:
- checkpoint screenshots
- `final_report.json` with PASS/FAIL per report field and final URLs for legal pages.
