# SaleADS Mi Negocio Full Workflow Test

This folder contains a standalone Playwright Python automation for:

- Google login flow
- Mi Negocio navigation and submenu checks
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Informacion General / Detalles de la Cuenta / Tus Negocios checks
- Legal links (`Terminos y Condiciones`, `Politica de Privacidad`) validation
- Checkpoint screenshots + final PASS/FAIL report

## Why this is environment-agnostic

- The script does **not** hardcode any SaleADS domain.
- Login URL is passed via CLI or environment variable.
- Selectors prioritize visible text and ARIA roles.
- It supports both same-tab and new-tab legal link behavior.

## Files

- `saleads_mi_negocio_full_test.py` - main workflow script
- `requirements.txt` - Python dependencies

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r qa/requirements.txt
python -m playwright install chromium
```

## Run

### Option A: Headed/manual (if browser already on login page)

```bash
python qa/saleads_mi_negocio_full_test.py
```

The script opens Chromium with a persistent profile and waits for navigation from `about:blank` to your SaleADS login page if `--login-url` is not provided.

### Option B: Headless/CI

```bash
python qa/saleads_mi_negocio_full_test.py \
  --headless \
  --login-url "https://<current-env-login-url>"
```

## Useful environment variables

- `SALEADS_LOGIN_URL`
- `SALEADS_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_TIMEOUT_MS` (default: `20000`)
- `SALEADS_HEADLESS` (`true`/`false`)
- `SALEADS_SLOW_MO_MS` (default: `0`)
- `SALEADS_ARTIFACTS_DIR` (default: `qa/artifacts`)
- `SALEADS_USER_DATA_DIR` (default: `qa/.pw-user-data`)

## Output artifacts

For each run, artifacts are stored in:

`qa/artifacts/saleads_mi_negocio_<timestamp>/`

Including:

- `screenshots/*.png`
- `final_report.json`

The final report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
