# SaleADS Mi Negocio full workflow test

This repository is not the SaleADS application source code, so this script provides an external end-to-end browser check for the Mi Negocio flow.

## Files

- `scripts/saleads_mi_negocio_full_test.py`
- `scripts/requirements-saleads-e2e.txt`

## What it validates

1. Login with Google (including optional account picker selection for `juanlucasbarbiergarzon@gmail.com`)
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios main sections
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones (same tab or new tab)
9. Politica de Privacidad (same tab or new tab)

It captures screenshots at critical checkpoints and writes a final PASS/FAIL report per required field.

## Install

```bash
python3 -m pip install -r scripts/requirements-saleads-e2e.txt
```

## Run

Pass a login URL for the active SaleADS environment (dev/staging/prod) without hardcoding inside the script:

```bash
python3 scripts/saleads_mi_negocio_full_test.py \
  --login-url "https://your-saleads-environment/login" \
  --headless
```

Or use environment variable:

```bash
export SALEADS_LOGIN_URL="https://your-saleads-environment/login"
python3 scripts/saleads_mi_negocio_full_test.py --headless
```

## Output artifacts

Each run creates:

- `target/saleads-mi-negocio/<timestamp>/report.json`
- `target/saleads-mi-negocio/<timestamp>/report.md`
- `target/saleads-mi-negocio/<timestamp>/screenshots/*.png`

The JSON report contains the final URL captured for legal pages and PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
