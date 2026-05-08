# SaleADS automation

This folder contains standalone browser automation scripts for SaleADS workflows.

## `saleads_mi_negocio_full_test.py`

Validates the full **Mi Negocio** workflow end-to-end:

1. Login with Google.
2. Open `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (same tab or new tab).
9. Validate `Política de Privacidad` (same tab or new tab).
10. Export final PASS/FAIL report by required fields.

### Environment variables

Required:

- `SALEADS_LOGIN_URL`: login page URL for the current environment (dev/staging/prod).

Optional:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_EXPECTED_USER_NAME` (if set, enforces exact user-name visibility)
- `SALEADS_HEADLESS` (default: `true`)
- `SALEADS_SLOW_MO_MS` (default: `0`)
- `SALEADS_TIMEOUT_MS` (default: `30000`)

### Local run

```bash
python3 -m pip install -r automation/requirements.txt
python3 -m playwright install chromium
SALEADS_LOGIN_URL="https://<current-env-login>" \
python3 automation/saleads_mi_negocio_full_test.py
```

### Outputs

- Screenshots: `automation/artifacts/saleads_mi_negocio_full_test/`
- JSON report: `automation/reports/saleads_mi_negocio_full_test_report.json`
