# SaleADS Mi Negocio Full Workflow Test

This folder contains a Playwright-based end-to-end script for validating the full Mi Negocio flow:

1. Login with Google
2. Open Mi Negocio menu
3. Validate "Agregar Negocio" modal
4. Open "Administrar Negocios"
5. Validate "Informacion General"
6. Validate "Detalles de la Cuenta"
7. Validate "Tus Negocios"
8. Validate "Terminos y Condiciones"
9. Validate "Politica de Privacidad"
10. Produce PASS/FAIL final report

The script is environment-agnostic and does not hardcode a SaleADS domain.

## Install dependencies

```bash
python3 -m pip install playwright
python3 -m playwright install chromium
```

## Run options

### Option A: Navigate directly to environment login URL

```bash
python3 automation/saleads_mi_negocio_full_test.py --start-url "https://your-env-login-url"
```

### Option B: Reuse an already-open browser session (CDP)

```bash
python3 automation/saleads_mi_negocio_full_test.py --cdp-url "http://127.0.0.1:9222"
```

## Useful arguments

- `--google-email "juanlucasbarbiergarzon@gmail.com"` (default)
- `--headed` to run with visible browser
- `--slow-mo-ms 200` for slower step execution
- `--output-dir automation/artifacts/my-run`

## Output artifacts

Each run writes:

- checkpoint screenshots (dashboard, menu, modal, account page, legal pages)
- `final_report.json` with per-step validations and final PASS/FAIL matrix
