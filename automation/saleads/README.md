# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic Playwright automation for:

- Google login flow
- Mi Negocio menu validation
- Agregar Negocio modal validation
- Administrar Negocios page validations
- Legal links validation (new tab or same-tab navigation)
- Screenshot evidence and PASS/FAIL report output

## Prerequisites

- Python 3.10+
- Chromium installed by Playwright

Install dependencies:

```bash
python3 -m pip install -r automation/saleads/requirements.txt
python3 -m playwright install chromium
```

## Usage

Set the login URL for the target environment (dev/staging/prod) and run:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-page>" \
python3 automation/saleads/saleads_mi_negocio_full_test.py --headless
```

Or pass it explicitly:

```bash
python3 automation/saleads/saleads_mi_negocio_full_test.py \
  --login-url "https://<current-environment-login-page>" \
  --headless
```

## Artifacts

Output is generated under:

`artifacts/saleads_mi_negocio/<timestamp>/`

Each run produces:

- `report.json`
- `report.md`
- Step screenshots at key checkpoints

## Notes

- The script does not hardcode any SaleADS domain.
- If Google OAuth requires password/passkey and no authenticated session is available, the `Login` step fails and dependent steps are marked as prerequisite failures.
