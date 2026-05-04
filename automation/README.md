## SaleADS Mi Negocio full workflow automation

This folder contains an environment-agnostic browser automation script for:

- Google login into SaleADS.ai
- Mi Negocio menu expansion checks
- Agregar Negocio modal checks
- Administrar Negocios page checks
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones and Política de Privacidad validation (popup or same-tab)

The automation stores screenshots and a JSON report with PASS/FAIL status per requested field.

### Files

- `saleads_mi_negocio_full_test.py`: main Playwright script
- `requirements.txt`: Python dependency list
- `artifacts/`: generated outputs (ignored by git)

### Prerequisites

- Python 3.10+
- Install dependencies:

```bash
python3 -m pip install -r automation/requirements.txt
python3 -m playwright install chromium
```

### Run

The script accepts a login URL from environment or CLI:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login>" python3 automation/saleads_mi_negocio_full_test.py
```

Or:

```bash
python3 automation/saleads_mi_negocio_full_test.py --login-url "https://<current-environment-login>"
```

Optional flags:

- `--headless / --no-headless` (default headless)
- `--timeout-ms 30000`
- `--google-email "juanlucasbarbiergarzon@gmail.com"`

### Output

- Run-specific report: `automation/artifacts/<timestamp>/report.json`
- Latest report pointer: `automation/artifacts/latest_report.json`
- Screenshots: `automation/artifacts/<timestamp>/screenshots/*.png`

### Notes

- No hardcoded SaleADS domain is used.
- Selectors prioritize visible text and include accent-insensitive matching.
- The script waits for UI load states after each click.
- If legal links open a new tab, the script validates the page, captures evidence, and returns to the app tab.
