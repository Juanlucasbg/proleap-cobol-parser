## SaleADS Mi Negocio full workflow test

This folder contains the automated test:

- `saleads_mi_negocio_full_test.py`

### What it validates

The script validates the full workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal
4. Administrar Negocios view
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones link
9. Politica de Privacidad link
10. Final PASS/FAIL report output

It captures screenshots at the required checkpoints and exports a JSON report.

### Install dependencies

```bash
python3 -m pip install -r e2e/requirements.txt
```

### Run

```bash
SALEADS_START_URL="https://<your-saleads-environment>/login" \
python3 e2e/saleads_mi_negocio_full_test.py
```

### Environment variables

- `SALEADS_START_URL` (recommended): login page URL for current environment.
- `SALEADS_GOOGLE_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_HEADLESS` (optional): `true` by default.
- `SALEADS_TIMEOUT_SECONDS` (optional): explicit wait timeout, default `35`.
- `SALEADS_ARTIFACTS_DIR` (optional): custom artifact folder.
- `SALEADS_REPORT_PATH` (optional): custom JSON report path.

### Artifacts

By default, artifacts are written under:

- `target/saleads_mi_negocio_full_test/<timestamp>/screenshots`
- `target/saleads_mi_negocio_full_test/<timestamp>/report.json`

