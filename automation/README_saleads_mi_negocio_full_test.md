# saleads_mi_negocio_full_test

End-to-end browser automation for:

1. Login with Google
2. Mi Negocio menu validation
3. Agregar Negocio modal validation
4. Administrar Negocios validation
5. Informacion General / Detalles de la Cuenta / Tus Negocios checks
6. Terminos y Condiciones + Politica de Privacidad validation
7. Screenshot and URL evidence capture
8. Final PASS/FAIL report by required section

## Requirements

- Python 3.10+
- Playwright

Install:

```bash
pip install playwright
python -m playwright install chromium
```

## Environment-agnostic usage

No domain is hardcoded in the script.

Choose one of these options:

- `--start-url`: provide the current environment login URL (dev/staging/prod)
- `--cdp-url`: attach to an existing browser session/tab already opened on the login page

## Run examples

Using a direct environment URL:

```bash
python automation/saleads_mi_negocio_full_test.py \
  --start-url "https://<current-environment-login-page>"
```

Attach to an existing browser session:

```bash
python automation/saleads_mi_negocio_full_test.py \
  --cdp-url "http://127.0.0.1:9222"
```

Optional flags:

- `--headed` to run non-headless
- `--slow-mo 250` to slow interactions
- `--timeout-ms 20000` for slower environments

## Artifacts

The script stores evidence under:

`artifacts/saleads_mi_negocio_full_test/<timestamp>/`

Generated files:

- Checkpoint screenshots
- `final_report.json`
- `final_report.txt`

The final report includes these required fields:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad

