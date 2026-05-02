## SaleADS Mi Negocio Full Test

This folder contains an environment-agnostic E2E workflow test:

- `saleads_mi_negocio_full_test.py`

The test implements:

1. Login with Google.
2. Mi Negocio menu expansion validation.
3. Agregar Negocio modal validation.
4. Administrar Negocios page validation.
5. Informacion General validation.
6. Detalles de la Cuenta validation.
7. Tus Negocios validation.
8. Terminos y Condiciones validation.
9. Politica de Privacidad validation.
10. Final PASS/FAIL report generation.

### Requirements

```bash
python3 -m pip install -r e2e/requirements.txt
python3 -m playwright install chromium
```

### Run options

#### Option A: dynamic login URL (no hardcoded domain)

```bash
export SALEADS_LOGIN_URL="https://<current-env>/login"
python3 e2e/saleads_mi_negocio_full_test.py --headless
```

#### Option B: attach to an already-open browser (login page already loaded)

```bash
export SALEADS_CDP_ENDPOINT="http://127.0.0.1:9222"
python3 e2e/saleads_mi_negocio_full_test.py
```

### Evidence output

Artifacts are generated under:

`e2e/artifacts/saleads_mi_negocio_full_test_<timestamp>/`

Expected outputs:

- Dashboard screenshot
- Mi Negocio expanded screenshot
- Agregar Negocio modal screenshot
- Administrar Negocios full screenshot
- Terminos y Condiciones screenshot
- Politica de Privacidad screenshot
- `final_report.json`
- `final_report.md`
