# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic Playwright automation script for validating the SaleADS "Mi Negocio" workflow end-to-end.

## What it validates

1. Login with Google.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal.
4. Administrar Negocios page sections.
5. Información General section.
6. Detalles de la Cuenta section.
7. Tus Negocios section.
8. Términos y Condiciones link/page.
9. Política de Privacidad link/page.

It captures screenshots at important checkpoints and writes a JSON report with PASS/FAIL results for each required field.

## Environment variables

- `SALEADS_LOGIN_URL` (required): Login page URL for the current SaleADS environment.
- `SALEADS_HEADLESS` (optional): `true` (default) or `false`.
- `SALEADS_STORAGE_STATE` (optional): Playwright storage-state JSON path if you want to preload authenticated state.

## Run

```bash
python3 -m pip install -r automation/requirements.txt
python3 -m playwright install chromium
python3 automation/saleads_mi_negocio_full_test.py
```

## Output

- JSON report: `artifacts/saleads_mi_negocio/<timestamp>/report.json`
- Screenshots: `artifacts/saleads_mi_negocio/<timestamp>/*.png`
