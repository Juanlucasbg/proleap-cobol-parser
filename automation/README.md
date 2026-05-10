# SaleADS UI automation

This folder contains the `saleads_mi_negocio_full_test` workflow automation.

## Prerequisites

- Python 3.10+
- Install dependencies:

```bash
python3 -m pip install -r automation/requirements.txt
python3 -m playwright install chromium
```

## Required environment variables

- `SALEADS_LOGIN_URL`: Login page URL for the target SaleADS environment (dev/staging/prod).

Optional:

- `SALEADS_HEADLESS` (default: `true`)
- `SALEADS_SLOW_MO_MS` (default: `0`)
- `SALEADS_EXPECTED_USER_NAME` (optional strict validation for the "Información General" user name)

## Run

```bash
python3 automation/saleads_mi_negocio_full_test.py
```

## Output

- JSON report and screenshots are generated under:
  - `automation/artifacts/<timestamp>/report.json`
  - `automation/artifacts/<timestamp>/*.png`
