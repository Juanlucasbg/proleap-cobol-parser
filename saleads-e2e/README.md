# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic UI E2E automation for:

- Google login
- Mi Negocio menu validation
- Agregar Negocio modal validation
- Administrar Negocios/account sections validation
- Legal links validation (`Términos y Condiciones`, `Política de Privacidad`)
- PASS/FAIL final report generation

## Requirements

- Python 3.10+
- Playwright for Python
- A reachable SaleADS login URL for the current environment (dev/staging/prod)

## Setup

```bash
python3 -m pip install -r saleads-e2e/requirements.txt
python3 -m playwright install chromium
```

## Run

```bash
export SALEADS_LOGIN_URL="https://<current-environment-login-page>"
export GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"
export HEADLESS="true"

python3 saleads-e2e/run_saleads_mi_negocio_test.py
```

## Output

Execution artifacts are written under:

`saleads-e2e/artifacts/<timestamp>/`

Including:

- step screenshots at important checkpoints
- `final_report.json`
- `final_report.md`
