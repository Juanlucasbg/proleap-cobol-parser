# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic browser automation test:

- **Test name:** `saleads_mi_negocio_full_test`
- **Script:** `saleads_mi_negocio_full_test.py`
- **Goal:** Validate the complete Mi Negocio workflow after Google login, including legal links.

## What it validates

The script runs all requested checks:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal
4. Administrar Negocios view
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones (including URL capture + screenshot)
9. Politica de Privacidad (including URL capture + screenshot)
10. Final PASS/FAIL report per section

It also captures screenshots at important checkpoints and writes a `final_report.json`.

## Runtime requirements

- Python 3.10+
- Google Chrome/Chromium
- ChromeDriver available to Selenium
- Python package:
  - `selenium`

Install dependency:

```bash
python3 -m pip install -r automation/requirements.txt
```

## Run

The test must receive the login URL at runtime (no hardcoded domain).

```bash
python3 automation/saleads_mi_negocio_full_test.py \
  --start-url "https://<current-environment-login-url>"
```

Optional flags:

- `--google-account-email` (default: `juanlucasbarbiergarzon@gmail.com`)
- `--output-dir` custom artifacts path
- `--headless` run in headless mode
- `--chrome-user-data-dir` reuse an existing Chrome profile/session

Equivalent environment variables:

- `SALEADS_START_URL`
- `SALEADS_GOOGLE_ACCOUNT_EMAIL`
- `SALEADS_OUTPUT_DIR`
- `SALEADS_HEADLESS`
- `SALEADS_CHROME_USER_DATA_DIR`

## Output artifacts

By default, artifacts are written under:

`automation/artifacts/saleads_mi_negocio_full_test_<timestamp>/`

Generated files include:

- `01_dashboard_loaded.png`
- `02_mi_negocio_menu_expanded.png`
- `03_agregar_negocio_modal.png`
- `04_administrar_negocios_page_full.png`
- `05_terminos_y_condiciones.png`
- `06_politica_de_privacidad.png`
- `final_report.json`

`final_report.json` includes:

- PASS/FAIL status for all required report fields
- Detailed check list per section
- Captured screenshot paths
- Final legal URLs
