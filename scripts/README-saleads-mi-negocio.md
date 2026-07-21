# SaleADS Mi Negocio Full Workflow Test

This script automates the full SaleADS.ai "Mi Negocio" workflow, including:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones
9. Validate Politica de Privacidad
10. Emit a PASS/FAIL report

## Install

```bash
python3 -m pip install -r scripts/requirements-saleads-e2e.txt
```

## Run

```bash
python3 scripts/saleads_mi_negocio_full_test.py --login-url "https://<your-saleads-environment>/login"
```

Or pass the URL through an environment variable:

```bash
export SALEADS_LOGIN_URL="https://<your-saleads-environment>/login"
python3 scripts/saleads_mi_negocio_full_test.py
```

Optional flags:

- `--headless` to run without opening a browser window
- `--google-account-email` to override the account chooser email
- `--artifacts-root` to control where screenshots and reports are saved

## Output

Execution artifacts are stored under:

`target/saleads-mi-negocio/<timestamp>/`

Generated files:

- `final_report.json`
- `final_report.md`
- screenshots for each required checkpoint
