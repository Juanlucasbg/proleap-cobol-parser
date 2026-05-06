## SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic Playwright runner:

- `saleads_mi_negocio_full_test.py`

The script validates the full flow requested by automation:

1. Login with Google.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal.
4. Administrar Negocios page sections.
5. Informacion General.
6. Detalles de la Cuenta.
7. Tus Negocios.
8. Terminos y Condiciones legal page.
9. Politica de Privacidad legal page.
10. Final PASS/FAIL report.

### Why it is environment-agnostic

- No hardcoded SaleADS domain is used.
- Target URL is provided externally through environment variables.
- Selectors are primarily based on visible text.

### Environment variables

- `SALEADS_LOGIN_URL`: Login page URL for the active environment (recommended).
- `SALEADS_GOOGLE_EMAIL`: Google account to select when the chooser appears (default: `juanlucasbarbiergarzon@gmail.com`).
- `SALEADS_HEADLESS`: `true`/`false` (default: `true`).
- `SALEADS_SLOWMO_MS`: Optional slow motion delay in milliseconds.
- `SALEADS_CDP_URL`: Optional Chrome DevTools endpoint to attach to an already-open browser/session.
- `SALEADS_SCREENSHOT_DIR`: Optional screenshot output directory.
- `SALEADS_REPORT_PATH`: Optional report output file path.

### Install

```bash
python3 -m pip install -r automation/requirements.txt
python3 -m playwright install chromium
```

### Run

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" \
python3 automation/saleads_mi_negocio_full_test.py
```

Or attach to an existing browser session:

```bash
SALEADS_CDP_URL="http://127.0.0.1:9222" \
python3 automation/saleads_mi_negocio_full_test.py
```

### Artifacts

- Screenshots: `automation/artifacts/screenshots/<timestamp>/`
- Final report: `automation/artifacts/reports/saleads_mi_negocio_full_test_report.json`
