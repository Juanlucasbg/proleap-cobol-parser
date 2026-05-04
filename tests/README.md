# SaleADS UI automation tests

This folder contains browser automation scripts used by cron-triggered Cursor automations.

## `saleads_mi_negocio_full_test.py`

Validates the complete **Mi Negocio** workflow requested by automation task `saleads_mi_negocio_full_test`, including:

- Google login handoff and account picker handling
- Sidebar + **Mi Negocio** expansion checks
- **Agregar Negocio** modal validation
- **Administrar Negocios** sections validation
- Legal links (**Términos y Condiciones** and **Política de Privacidad**) with both same-tab and new-tab flows
- Checkpoint screenshots and final PASS/FAIL report

### Requirements

- Python 3.10+
- Playwright Python package
- Chromium browser for Playwright

Install dependencies:

```bash
pip3 install pytest playwright
python3 -m playwright install --with-deps chromium
```

### Usage

Run against any SaleADS environment by passing the current environment login URL at runtime (no hardcoded domain):

```bash
python3 tests/saleads_mi_negocio_full_test.py --base-url "https://<current-saleads-login-url>"
```

Or with environment variable:

```bash
export SALEADS_BASE_URL="https://<current-saleads-login-url>"
python3 tests/saleads_mi_negocio_full_test.py
```

### Artifacts

By default, outputs are written to:

- `artifacts/saleads_mi_negocio_full_test/screenshots/*.png`
- `artifacts/saleads_mi_negocio_full_test/report.json`

Override with:

```bash
python3 tests/saleads_mi_negocio_full_test.py \
  --base-url "https://<current-saleads-login-url>" \
  --artifacts-dir "artifacts/custom_run"
```
