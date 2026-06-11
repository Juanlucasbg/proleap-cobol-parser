# SaleADS Mi Negocio full workflow automation

This folder contains an environment-agnostic Playwright script for the workflow:

1. Login with Google.
2. Validate `Mi Negocio` menu and submenu options.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios` and validate required sections.
5. Validate legal links (`Terminos y Condiciones`, `Politica de Privacidad`) including popup/same-tab behavior.
6. Generate PASS/FAIL report plus screenshots.

## Prerequisites

- Python 3.10+
- Playwright package and Chromium browser

Install:

```bash
python3 -m pip install -r automation/requirements.txt
python3 -m playwright install chromium
```

## Environment variables

- `SALEADS_LOGIN_URL` (required): login URL for the target SaleADS environment.
- `GOOGLE_ACCOUNT_EMAIL` (optional, default: `juanlucasbarbiergarzon@gmail.com`)
- `GOOGLE_ACCOUNT_PASSWORD` (optional): required only when Google asks for password and fully autonomous login is desired.
- `PW_HEADLESS` (optional, default: `true`)
- `SALEADS_ARTIFACTS_DIR` (optional, default: `automation/artifacts`)

## Run

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env-login-url>" \
python3 automation/saleads_mi_negocio_full_test.py
```

## Outputs

- JSON report: `automation/artifacts/reports/saleads_mi_negocio_full_test_report.json`
- Markdown report: `automation/artifacts/reports/saleads_mi_negocio_full_test_report.md`
- Screenshots: `automation/artifacts/screenshots/`
