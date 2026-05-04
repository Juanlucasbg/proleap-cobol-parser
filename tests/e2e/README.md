# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic Playwright script that validates
the full **Mi Negocio** workflow (not only login) for SaleADS.ai.

## Files

- `saleads_mi_negocio_full_test.py`: end-to-end workflow script.
- `requirements.txt`: Python dependencies.

## What it validates

The script executes and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

It also:

- waits for UI load after clicks,
- prefers text-based selectors,
- handles legal links that open in same tab or new tab,
- captures screenshots at key checkpoints,
- writes a machine-readable final report JSON.

## Required environment variables

- `SALEADS_BASE_URL`: login page URL for the current target environment.

Optional:

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS` (`true`/`false`, default: `false`)
- `SALEADS_TIMEOUT_MS` (default: `20000`)
- `SALEADS_ARTIFACTS_DIR` (default: `tests/e2e/artifacts/<run-id>`)

## Run

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r tests/e2e/requirements.txt
python -m playwright install chromium

export SALEADS_BASE_URL="https://<your-environment-login-url>"
export GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"
python tests/e2e/saleads_mi_negocio_full_test.py
```

## Output

Artifacts are stored under:

- `tests/e2e/artifacts/<run-id>/screenshots/`
- `tests/e2e/artifacts/<run-id>/final_report.json`
