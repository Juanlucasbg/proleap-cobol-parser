# SaleADS Mi Negocio workflow automation

This folder contains an end-to-end Playwright script that validates the full
`Mi Negocio` module workflow after Google login.

## Test covered

Script: `saleads_mi_negocio_full_test.py`

The script executes and validates:

1. Login with Google and dashboard/sidebar visibility.
2. Expansion of `Mi Negocio` menu in the left navigation.
3. `Agregar Negocio` modal content.
4. `Administrar Negocios` page sections.
5. `Información General` section.
6. `Detalles de la Cuenta` section.
7. `Tus Negocios` section.
8. `Términos y Condiciones` link navigation/new-tab behavior.
9. `Política de Privacidad` link navigation/new-tab behavior.
10. Final PASS/FAIL JSON report.

It captures screenshots for key checkpoints and stores final legal URLs.

## Environment-agnostic behavior

- No hardcoded SaleADS domain is used.
- Provide the login URL for the target environment at runtime through
  `SALEADS_START_URL`.
- UI elements are selected primarily by visible text (Spanish and common
  English alternatives for login).

## Required environment variables

- `SALEADS_START_URL` (required): login page URL for the current SaleADS env.

## Optional environment variables

- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (default:
  `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_EXPECTED_USER_NAME` (default: unset)
- `SALEADS_HEADLESS` (default: `true`)
- `SALEADS_TIMEOUT_MS` (default: `25000`)
- `SALEADS_SCREENSHOT_DIR` (default: `automation/artifacts`)

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r automation/requirements.txt
python -m playwright install chromium
```

## Run

```bash
export SALEADS_START_URL="https://<current-env>/login"
export SALEADS_GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"
python automation/saleads_mi_negocio_full_test.py
```

## Output artifacts

For each run, a timestamped folder is created under `automation/artifacts`:

- numbered screenshots (`01_...png`, `02_...png`, ...)
- `final_report.json` with:
  - PASS/FAIL per requested report field
  - step details
  - captured legal page URLs
  - screenshot directory path
