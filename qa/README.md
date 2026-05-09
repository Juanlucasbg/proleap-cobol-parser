# SaleADS Mi Negocio E2E

This folder contains a standalone Playwright (Python) end-to-end test for:

- Google login
- Mi Negocio menu workflow
- Agregar Negocio modal validation
- Administrar Negocios account view validation
- Legal links (Términos y Condiciones / Política de Privacidad) validation
- Step-by-step PASS/FAIL reporting
- Screenshot evidence at key checkpoints

## Test file

- `saleads_mi_negocio_full_test.py`

## Why it is environment-agnostic

- No hardcoded SaleADS domain is used.
- The start URL is passed at runtime with `--url` or `SALEADS_START_URL`.
- Selectors prioritize visible text over environment-specific CSS selectors.

## Prerequisites

1. Python 3.10+
2. Playwright Python package and browser:

```bash
pip install playwright
playwright install chromium
```

## Run

```bash
python qa/saleads_mi_negocio_full_test.py --url "https://<your-saleads-environment>/login"
```

Optional:

```bash
python qa/saleads_mi_negocio_full_test.py \
  --url "https://<your-saleads-environment>/login" \
  --artifacts-dir "qa/artifacts/saleads_mi_negocio_full_test" \
  --headless
```

## Artifacts

The script writes:

- checkpoint screenshots (`*.png`)
- final report (`report.json`)

Default location:

- `qa/artifacts/saleads_mi_negocio_full_test/`

The JSON report includes:

- PASS/FAIL for each requested validation area
- details for each step
- final URLs for legal pages
