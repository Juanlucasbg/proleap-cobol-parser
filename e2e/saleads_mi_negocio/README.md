# SaleADS Mi Negocio Full Workflow Test

This directory contains an environment-agnostic end-to-end UI test for the SaleADS **Mi Negocio** module workflow:

1. Login with Google (continue beyond login)
2. Open Mi Negocio menu and validate submenu options
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones (including URL capture)
9. Validate Politica de Privacidad (including URL capture)
10. Produce PASS/FAIL final report

## Environment assumptions

- The test works in any SaleADS environment (dev/staging/prod).
- No hardcoded domain is used.
- Use one of:
  - `SALEADS_BASE_URL` to navigate to the app, or
  - pre-open the browser on the login page before executing.

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
playwright install chromium
```

## Run

```bash
pytest test_saleads_mi_negocio_full_workflow.py
```

Optional:

```bash
SALEADS_BASE_URL="https://your-environment.example" pytest test_saleads_mi_negocio_full_workflow.py
```

If no URL is provided, the test expects the browser to already be on a SaleADS login page.
When launched on a blank page without `SALEADS_BASE_URL`, it fails fast with a precondition error.

## Artifacts

- Screenshots: `artifacts/screenshots/*.png`
- Final report JSON: `artifacts/saleads_mi_negocio_report.json`

The JSON report includes:
- PASS/FAIL for each required report field
- Step-level details
- Screenshot paths
- Captured legal-page final URLs
